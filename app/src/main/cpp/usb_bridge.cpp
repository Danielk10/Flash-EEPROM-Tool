// ════════════════════════════════════════════════════════════════════════
// usb_bridge.cpp — Motor nativo del puente USB-Serie ↔ flashrom
//
// Sustituye a los hilos Java de PtyBridge (UsbRequest + byte[] + JNI por
// paquete) por dos hilos POSIX que mueven los bytes directamente entre:
//
//   • el endpoint bulk del adaptador USB-serie (ioctl USBDEVFS_BULK sobre el
//     FD de UsbDeviceConnection; la interfaz ya fue reclamada y configurada
//     —baudios, DTR/RTS— por usb-serial-for-android en Java), y
//   • un "commFd" que flashrom usa como puerto serie:
//       - Modo PTY:    master del pseudo-terminal (flashrom -p serprog:dev=/dev/pts/N)
//       - Modo Socket: socket TCP en 127.0.0.1 (flashrom -p serprog:ip=127.0.0.1:PUERTO)
//
// Gracias a que en POSIX "todo es un descriptor", el mismo bucle sirve para
// ambos transportes. La JVM queda fuera del flujo de datos en tiempo real:
// sin byte[] en el heap, sin pausas del GC y sin cruces JNI por paquete.
//
// Notas de diseño:
//   • El IN se lee de a UN paquete (wMaxPacketSize) por ioctl. Con usbfs, si
//     un USBDEVFS_BULK expira con datos parciales, esos datos se descartan;
//     leer exactamente un paquete hace que cada URB se complete en cuanto
//     llega un paquete, eliminando esa ventana de pérdida.
//   • FTDI antepone 2 bytes de estado a cada paquete IN: se eliminan aquí
//     (lo mismo que hace FtdiSerialPort.read() en Java).
//   • Las escrituras a socket usan MSG_NOSIGNAL: si flashrom cierra la
//     conexión no se dispara SIGPIPE (que mataría el proceso de la app).
//   • Todos los FDs que crea este módulo llevan O_CLOEXEC para que el
//     proceso hijo flashrom (fork+exec) no los herede.
// ════════════════════════════════════════════════════════════════════════

#include <jni.h>
#include <pthread.h>
#include <atomic>
#include <mutex>
#include <string>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <unistd.h>
#include <fcntl.h>
#include <poll.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <linux/usbdevice_fs.h>
#include <android/log.h>

#define BR_TAG "UsbNativeBridge"
#define BR_LOGI(...) __android_log_print(ANDROID_LOG_INFO, BR_TAG, __VA_ARGS__)
#define BR_LOGW(...) __android_log_print(ANDROID_LOG_WARN, BR_TAG, __VA_ARGS__)
#define BR_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, BR_TAG, __VA_ARGS__)

namespace {

constexpr int kCommChunk = 4096;        // Lectura máx. desde flashrom (PTY/socket) por iteración
constexpr int kUsbInTimeoutMs = 100;    // Timeout de cada lectura bulk IN (también acota la latencia de stop)
constexpr int kUsbOutTimeoutMs = 1000;  // Timeout de escritura bulk OUT
constexpr int kPollTimeoutMs = 200;     // poll() sobre commFd / socket de escucha
constexpr int kCommWriteStallMs = 500;  // Máx. espera para escribir hacia flashrom antes de descartar
constexpr int kMinPacket = 8;
constexpr int kMaxPacket = 1024;
constexpr uint8_t kBeacon1 = 0xAA;
constexpr uint8_t kBeacon2 = 0x55;
constexpr uint8_t kSyncNop = 0x10;

struct Bridge {
    // Configuración (inmutable tras start)
    int usbFd = -1;
    unsigned epIn = 0;
    unsigned epOut = 0;
    int inPacket = 64;
    int ptyFd = -1;      // Modo PTY: master FD (propiedad de Java/ParcelFileDescriptor, NO se cierra aquí)
    int listenFd = -1;   // Modo Socket: socket de escucha (propiedad del bridge, se cierra en stop)
    bool ftdi = false;
    bool beaconResync = false;

    int wakePipe[2] = {-1, -1};
    std::atomic<bool> running{false};

    // FD destino para USB→flashrom. Protegido por clientMutex para que el hilo
    // de aceptación no lo cierre (y el kernel lo reutilice) mientras se escribe.
    std::mutex clientMutex;
    int clientFd = -1;
    bool clientIsSocket = false;

    pthread_t tComm{};
    pthread_t tUsb{};
    bool tCommStarted = false;
    bool tUsbStarted = false;

    // Estadísticas (lectura desde Java para el reporte de diagnóstico)
    std::atomic<uint64_t> usbRxBytes{0};
    std::atomic<uint64_t> usbTxBytes{0};
    std::atomic<uint64_t> usbRxXfers{0};
    std::atomic<uint64_t> usbTxXfers{0};
    std::atomic<uint64_t> usbErrors{0};
    std::atomic<uint64_t> commErrors{0};
    std::atomic<uint64_t> clients{0};
    std::atomic<uint64_t> resyncs{0};
    std::atomic<uint64_t> droppedBytes{0};
    std::atomic<bool> deviceGone{false};

    std::mutex errMutex;
    std::string lastError = "none";

    void setError(const char *fmt, ...) {
        char buf[256];
        va_list ap;
        va_start(ap, fmt);
        vsnprintf(buf, sizeof(buf), fmt, ap);
        va_end(ap);
        BR_LOGW("%s", buf);
        std::lock_guard<std::mutex> lk(errMutex);
        lastError = buf;
    }
};

void raiseThreadPriority() {
    // Equivalente a THREAD_PRIORITY_URGENT_DISPLAY (-8). Best-effort: si el
    // sistema no lo permite, se sigue con la prioridad por defecto.
    pid_t tid = (pid_t) syscall(SYS_gettid);
    if (setpriority(PRIO_PROCESS, (id_t) tid, -8) != 0) {
        BR_LOGW("setpriority(-8) no permitido (errno=%d), se usa prioridad normal", errno);
    }
}

int usbBulk(int fd, unsigned ep, void *data, unsigned len, unsigned timeoutMs) {
    struct usbdevfs_bulktransfer bulk;
    memset(&bulk, 0, sizeof(bulk));
    bulk.ep = ep;
    bulk.len = len;
    bulk.timeout = timeoutMs;
    bulk.data = data;
    int r;
    do {
        r = ioctl(fd, USBDEVFS_BULK, &bulk);
    } while (r < 0 && errno == EINTR);
    return r;
}

bool isDeviceGoneErrno(int e) {
    return e == ENODEV || e == ESHUTDOWN || e == EBADF;
}

/** Envía len bytes al endpoint OUT. Devuelve true si se transmitieron todos. */
bool usbWriteAll(Bridge *b, const uint8_t *data, int len) {
    int off = 0;
    while (off < len && b->running.load(std::memory_order_relaxed)) {
        int r = usbBulk(b->usbFd, b->epOut, const_cast<uint8_t *>(data + off),
                        (unsigned) (len - off), kUsbOutTimeoutMs);
        if (r < 0) {
            int e = errno;
            b->usbErrors++;
            if (isDeviceGoneErrno(e)) {
                b->deviceGone = true;
                b->setError("usb-out: dispositivo desconectado (errno=%d)", e);
            } else {
                b->setError("usb-out: errno=%d (%s)", e, strerror(e));
            }
            return false;
        }
        b->usbTxXfers++;
        b->usbTxBytes += (uint64_t) r;
        off += r;
        if (r == 0) break;  // No debería ocurrir con timeout > 0
    }
    return off == len;
}

/**
 * Escribe len bytes hacia flashrom (PTY master o socket). Usa poll(POLLOUT)
 * para no bloquearse indefinidamente si flashrom no está leyendo.
 * Devuelve: >= 0 bytes escritos, -1 si el par cerró la conexión.
 */
int commWriteAll(Bridge *b, int fd, bool isSocket, const uint8_t *data, int len) {
    int off = 0;
    int waitedMs = 0;
    while (off < len && b->running.load(std::memory_order_relaxed)) {
        struct pollfd p = {fd, POLLOUT, 0};
        int pr = poll(&p, 1, 50);
        if (pr < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (pr == 0) {
            waitedMs += 50;
            if (waitedMs >= kCommWriteStallMs) break;  // Nadie lee: descartar el resto
            continue;
        }
        if (p.revents & (POLLERR | POLLNVAL)) return -1;
        if (isSocket && (p.revents & POLLHUP)) return -1;

        ssize_t w = isSocket ? send(fd, data + off, (size_t) (len - off), MSG_NOSIGNAL | MSG_DONTWAIT)
                             : write(fd, data + off, (size_t) (len - off));
        if (w < 0) {
            int e = errno;
            if (e == EINTR || e == EAGAIN || e == EWOULDBLOCK) continue;
            if (e == EPIPE || e == ECONNRESET || e == ENOTCONN) return -1;
            b->setError("comm-write: errno=%d (%s)", e, strerror(e));
            return -1;
        }
        off += (int) w;
        waitedMs = 0;
    }
    return off;
}

// ── Hilo A: flashrom (PTY master / socket) → USB OUT ────────────────────
void *commToUsbThread(void *arg) {
    auto *b = static_cast<Bridge *>(arg);
    raiseThreadPriority();
    const bool socketMode = b->listenFd >= 0;
    uint8_t buf[kCommChunk];

    BR_LOGI("Hilo comm→usb iniciado (modo %s)", socketMode ? "Socket TCP" : "PTY");

    while (b->running.load()) {
        int fd;
        if (socketMode) {
            // Esperar a que flashrom se conecte (una conexión por ejecución de flashrom)
            struct pollfd p[2] = {{b->listenFd, POLLIN, 0}, {b->wakePipe[0], POLLIN, 0}};
            int r = poll(p, 2, kPollTimeoutMs);
            if (r < 0 && errno != EINTR) {
                b->setError("poll(listen): errno=%d", errno);
                usleep(20000);
                continue;
            }
            if (r <= 0) continue;
            if (p[1].revents) break;
            if (!(p[0].revents & POLLIN)) continue;

            int c = accept4(b->listenFd, nullptr, nullptr, SOCK_CLOEXEC);
            if (c < 0) {
                if (errno != EINTR && errno != EAGAIN && errno != ECONNABORTED) {
                    b->setError("accept4: errno=%d", errno);
                    usleep(20000);
                }
                continue;
            }
            int one = 1;
            setsockopt(c, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one));  // Sin Nagle: latencia mínima
            {
                std::lock_guard<std::mutex> lk(b->clientMutex);
                b->clientFd = c;
                b->clientIsSocket = true;
            }
            b->clients++;
            BR_LOGI("flashrom conectado al socket local (fd=%d, conexión #%llu)",
                    c, (unsigned long long) b->clients.load());
            fd = c;
        } else {
            fd = b->ptyFd;
        }

        // Bucle de datos
        while (b->running.load()) {
            struct pollfd p[2] = {{fd, POLLIN, 0}, {b->wakePipe[0], POLLIN, 0}};
            int r = poll(p, 2, kPollTimeoutMs);
            if (r < 0) {
                if (errno == EINTR) continue;
                b->setError("poll(comm): errno=%d", errno);
                break;
            }
            if (r == 0) continue;
            if (p[1].revents) break;

            if (p[0].revents & POLLIN) {
                ssize_t n = read(fd, buf, sizeof(buf));
                if (n > 0) {
                    if (!usbWriteAll(b, buf, (int) n) && b->deviceGone.load()) {
                        b->running = false;
                        break;
                    }
                } else if (n == 0) {
                    if (socketMode) break;  // flashrom cerró la conexión
                    usleep(5000);
                } else {
                    int e = errno;
                    if (e == EINTR || e == EAGAIN || e == EWOULDBLOCK) continue;
                    if (socketMode) break;
                    // PTY: EIO aparece si no hay ningún extremo slave abierto.
                    b->commErrors++;
                    b->setError("pty-read: errno=%d (%s)", e, strerror(e));
                    usleep(10000);
                }
            } else if (p[0].revents & (POLLHUP | POLLERR | POLLNVAL)) {
                if (socketMode) break;
                usleep(10000);  // PTY sin slave abierto: evitar busy-loop
            }
        }

        if (socketMode) {
            {
                std::lock_guard<std::mutex> lk(b->clientMutex);
                if (b->clientFd == fd) b->clientFd = -1;
            }
            close(fd);
            BR_LOGI("Conexión de flashrom cerrada; esperando la siguiente");
        } else {
            break;  // En modo PTY sólo se sale al detener el bridge
        }
    }
    BR_LOGI("Hilo comm→usb finalizado (usbTx=%llu B)", (unsigned long long) b->usbTxBytes.load());
    return nullptr;
}

// ── Hilo B: USB IN → flashrom (PTY master / socket) ─────────────────────
void *usbToCommThread(void *arg) {
    auto *b = static_cast<Bridge *>(arg);
    raiseThreadPriority();
    uint8_t pkt[kMaxPacket];
    const unsigned readLen = (unsigned) b->inPacket;
    int prevByte = -1;

    BR_LOGI("Hilo usb→comm iniciado (ep=0x%02X, paquete=%u B, ftdi=%d, resync=%d)",
            b->epIn, readLen, b->ftdi ? 1 : 0, b->beaconResync ? 1 : 0);

    while (b->running.load()) {
        int r = usbBulk(b->usbFd, b->epIn, pkt, readLen, kUsbInTimeoutMs);
        if (r < 0) {
            int e = errno;
            if (e == ETIMEDOUT || e == EAGAIN) continue;  // Sin datos: normal
            if (isDeviceGoneErrno(e)) {
                b->deviceGone = true;
                b->setError("usb-in: dispositivo desconectado (errno=%d)", e);
                b->running = false;
                break;
            }
            b->usbErrors++;
            b->setError("usb-in: errno=%d (%s)", e, strerror(e));
            usleep(20000);
            continue;
        }
        b->usbRxXfers++;

        const uint8_t *data = pkt;
        int n = r;
        if (b->ftdi) {
            if (n <= 2) continue;  // Paquete de sólo estado (latency timer)
            data += 2;
            n -= 2;
        }
        if (n <= 0) continue;
        b->usbRxBytes += (uint64_t) n;

        // Beacon de resincronización del firmware serprog (0xAA 0x55): mismo
        // comportamiento que la versión Java (responder con SYNCNOP), pero
        // detectando también beacons partidos entre dos paquetes USB.
        if (b->beaconResync) {
            bool beacon = false;
            if (prevByte == kBeacon1 && data[0] == kBeacon2) beacon = true;
            for (int i = 0; !beacon && i + 1 < n; i++) {
                if (data[i] == kBeacon1 && data[i + 1] == kBeacon2) beacon = true;
            }
            if (beacon) {
                b->resyncs++;
                uint8_t sync = kSyncNop;
                usbWriteAll(b, &sync, 1);
                BR_LOGI("Beacon 0xAA55 detectado — SYNCNOP enviado (resync #%llu)",
                        (unsigned long long) b->resyncs.load());
            }
        }
        prevByte = data[n - 1];

        std::lock_guard<std::mutex> lk(b->clientMutex);
        int fd = b->clientFd;
        if (fd < 0) {
            b->droppedBytes += (uint64_t) n;  // Socket: flashrom aún no conectado
            continue;
        }
        int w = commWriteAll(b, fd, b->clientIsSocket, data, n);
        if (w < n) {
            b->commErrors++;
            b->droppedBytes += (uint64_t) (n - (w > 0 ? w : 0));
        }
    }
    BR_LOGI("Hilo usb→comm finalizado (usbRx=%llu B)", (unsigned long long) b->usbRxBytes.load());
    return nullptr;
}

void destroyBridge(Bridge *b) {
    if (!b) return;
    b->running = false;
    if (b->wakePipe[1] >= 0) {
        uint8_t one = 1;
        ssize_t ignored = write(b->wakePipe[1], &one, 1);
        (void) ignored;
    }
    if (b->tCommStarted) pthread_join(b->tComm, nullptr);
    if (b->tUsbStarted) pthread_join(b->tUsb, nullptr);
    if (b->listenFd >= 0) close(b->listenFd);
    if (b->wakePipe[0] >= 0) close(b->wakePipe[0]);
    if (b->wakePipe[1] >= 0) close(b->wakePipe[1]);
    delete b;
}

}  // namespace

// ════════════════════════════════════════════════════════════════════════
// JNI
// ════════════════════════════════════════════════════════════════════════

/**
 * Crea un servidor TCP en 127.0.0.1 con puerto efímero.
 * @return [listenFd, puerto] o null si falla.
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_com_diamon_curso_core_PtyBridge_createLoopbackServer(JNIEnv *env, jclass) {
    int fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        BR_LOGE("socket() falló: errno=%d", errno);
        return nullptr;
    }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = 0;  // Puerto efímero asignado por el kernel
    if (bind(fd, reinterpret_cast<sockaddr *>(&addr), sizeof(addr)) != 0) {
        BR_LOGE("bind(127.0.0.1:0) falló: errno=%d", errno);
        close(fd);
        return nullptr;
    }
    if (listen(fd, 1) != 0) {
        BR_LOGE("listen() falló: errno=%d", errno);
        close(fd);
        return nullptr;
    }
    socklen_t len = sizeof(addr);
    if (getsockname(fd, reinterpret_cast<sockaddr *>(&addr), &len) != 0) {
        BR_LOGE("getsockname() falló: errno=%d", errno);
        close(fd);
        return nullptr;
    }
    int port = ntohs(addr.sin_port);
    BR_LOGI("Servidor loopback escuchando en 127.0.0.1:%d (fd=%d)", port, fd);

    jintArray result = env->NewIntArray(2);
    if (result == nullptr) {
        close(fd);
        return nullptr;
    }
    jint arr[2] = {fd, port};
    env->SetIntArrayRegion(result, 0, 2, arr);
    return result;
}

/**
 * Arranca el motor nativo. Exactamente uno de ptyFd / listenFd debe ser >= 0.
 * Si se usa listenFd, su propiedad pasa al bridge (se cierra en nativeBridgeStop),
 * salvo que esta función falle (retorna 0), en cuyo caso el llamador lo cierra.
 *
 * @return handle opaco (> 0) o 0 si no se pudo arrancar.
 */
extern "C" JNIEXPORT jlong JNICALL
Java_com_diamon_curso_core_PtyBridge_nativeBridgeStart(
        JNIEnv *, jclass,
        jint usbFd, jint epIn, jint epOut, jint inPacket,
        jint ptyFd, jint listenFd, jboolean ftdi, jboolean beaconResync) {

    if (usbFd < 0 || (epIn & 0x80) == 0 || (epOut & 0x80) != 0) {
        BR_LOGE("nativeBridgeStart: parámetros USB inválidos (fd=%d in=0x%02X out=0x%02X)",
                (int) usbFd, (int) epIn, (int) epOut);
        return 0;
    }
    if ((ptyFd >= 0) == (listenFd >= 0)) {
        BR_LOGE("nativeBridgeStart: se requiere exactamente un commFd (pty=%d listen=%d)",
                (int) ptyFd, (int) listenFd);
        return 0;
    }

    auto *b = new Bridge();
    b->usbFd = usbFd;
    b->epIn = (unsigned) epIn;
    b->epOut = (unsigned) epOut;
    int pk = inPacket;
    if (pk < kMinPacket) pk = 64;
    if (pk > kMaxPacket) pk = kMaxPacket;
    b->inPacket = pk;
    b->ptyFd = ptyFd;
    b->ftdi = ftdi == JNI_TRUE;
    b->beaconResync = beaconResync == JNI_TRUE;

    if (pipe2(b->wakePipe, O_CLOEXEC | O_NONBLOCK) != 0) {
        BR_LOGE("pipe2() falló: errno=%d", errno);
        delete b;
        return 0;
    }

    if (ptyFd >= 0) {
        b->clientFd = ptyFd;
        b->clientIsSocket = false;
    }

    b->running = true;
    if (pthread_create(&b->tUsb, nullptr, usbToCommThread, b) != 0) {
        BR_LOGE("pthread_create(usb→comm) falló");
        destroyBridge(b);
        return 0;
    }
    b->tUsbStarted = true;

    // Asignar listenFd justo antes de crear el hilo que lo usa: si algo falló
    // antes, el llamador conserva la propiedad y lo cierra él mismo.
    b->listenFd = listenFd;
    if (pthread_create(&b->tComm, nullptr, commToUsbThread, b) != 0) {
        BR_LOGE("pthread_create(comm→usb) falló");
        b->listenFd = -1;  // Devolver la propiedad al llamador
        destroyBridge(b);
        return 0;
    }
    b->tCommStarted = true;

    BR_LOGI("Motor nativo activo: usbFd=%d in=0x%02X out=0x%02X pkt=%d modo=%s",
            (int) usbFd, (int) epIn, (int) epOut, pk, listenFd >= 0 ? "Socket" : "PTY");
    return reinterpret_cast<jlong>(b);
}

/** Detiene los hilos y libera recursos propios del bridge (no cierra usbFd ni ptyFd). */
extern "C" JNIEXPORT void JNICALL
Java_com_diamon_curso_core_PtyBridge_nativeBridgeStop(JNIEnv *, jclass, jlong handle) {
    destroyBridge(reinterpret_cast<Bridge *>(handle));
}

/** True si los hilos nativos siguen activos (false tras desconexión USB). */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_diamon_curso_core_PtyBridge_nativeBridgeIsRunning(JNIEnv *, jclass, jlong handle) {
    auto *b = reinterpret_cast<Bridge *>(handle);
    return (b != nullptr && b->running.load()) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Estadísticas: [usbRxBytes, usbTxBytes, usbRxXfers, usbTxXfers, usbErrors,
 *                commErrors, clients, resyncs, droppedBytes, deviceGone]
 */
extern "C" JNIEXPORT jlongArray JNICALL
Java_com_diamon_curso_core_PtyBridge_nativeBridgeStats(JNIEnv *env, jclass, jlong handle) {
    auto *b = reinterpret_cast<Bridge *>(handle);
    if (b == nullptr) return nullptr;
    jlong v[10] = {
            (jlong) b->usbRxBytes.load(), (jlong) b->usbTxBytes.load(),
            (jlong) b->usbRxXfers.load(), (jlong) b->usbTxXfers.load(),
            (jlong) b->usbErrors.load(), (jlong) b->commErrors.load(),
            (jlong) b->clients.load(), (jlong) b->resyncs.load(),
            (jlong) b->droppedBytes.load(), (jlong) (b->deviceGone.load() ? 1 : 0)};
    jlongArray arr = env->NewLongArray(10);
    if (arr != nullptr) env->SetLongArrayRegion(arr, 0, 10, v);
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_curso_core_PtyBridge_nativeBridgeLastError(JNIEnv *env, jclass, jlong handle) {
    auto *b = reinterpret_cast<Bridge *>(handle);
    if (b == nullptr) return env->NewStringUTF("none");
    std::lock_guard<std::mutex> lk(b->errMutex);
    return env->NewStringUTF(b->lastError.c_str());
}
