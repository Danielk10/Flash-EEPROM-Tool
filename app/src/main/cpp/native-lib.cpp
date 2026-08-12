#include <jni.h>
#include <string>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/wait.h>
#include <termios.h>
#include <poll.h>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "FlashromJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ════════════════════════════════════════════════════════════════════════
// FD Duplication para FlashromExecutor:
// dup() crea un nuevo FD SIN O_CLOEXEC, permitiendo que el proceso hijo
// (flashrom via ProcessBuilder) herede el file descriptor del USB.
// Sin esto, Android cierra el FD original durante exec() porque tiene
// O_CLOEXEC, y flashrom recibe un FD inválido.
// ════════════════════════════════════════════════════════════════════════

/**
 * Duplica el file descriptor USB para que sea heredable por procesos hijos.
 * dup() en Linux crea un nuevo FD sin la flag O_CLOEXEC, lo que permite
 * que sobreviva a fork()+exec() cuando ProcessBuilder lanza flashrom.
 *
 * @param fd  El FD original de UsbDeviceConnection.getFileDescriptor()
 * @return    Nuevo FD heredable (>= 0), o -1 en error.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_diamon_curso_core_FlashromExecutor_dupFdForChild(JNIEnv *env, jclass clazz, jint fd) {
    if (fd < 0) {
        LOGE("dupFdForChild: FD inválido (%d)", (int) fd);
        return -1;
    }

    int newFd = dup((int) fd);
    if (newFd < 0) {
        LOGE("dupFdForChild: dup(%d) falló, errno=%d", (int) fd, errno);
        return -1;
    }

    // Garantizar que O_CLOEXEC NO está activo (dup() ya lo hace, pero
    // lo forzamos explícitamente por seguridad ante variantes de kernel).
    int flags = fcntl(newFd, F_GETFD);
    if (flags >= 0 && (flags & FD_CLOEXEC)) {
        fcntl(newFd, F_SETFD, flags & ~FD_CLOEXEC);
        LOGI("dupFdForChild: O_CLOEXEC removido explícitamente del FD %d", newFd);
    }

    LOGI("dupFdForChild: FD %d -> %d (heredable por proceso hijo)", (int) fd, newFd);
    return (jint) newFd;
}

/**
 * Cierra un FD duplicado previamente por dupFdForChild().
 * Llamar DESPUÉS de que el proceso hijo (flashrom) haya terminado.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_diamon_curso_core_FlashromExecutor_closeDupedFd(JNIEnv *env, jclass clazz, jint fd) {
    if (fd >= 0) {
        LOGI("closeDupedFd: cerrando FD %d", (int) fd);
        close((int) fd);
    }
}

/**
 * Crea un par pseudo-terminal (PTY) y devuelve [masterFdStr, slavePath].
 * El slave path (e.g. "/dev/pts/3") se pasa a flashrom como:
 *   -p serprog:dev=/dev/pts/3:115200
 * El master FD se usa en Java para puentear bytes con usb-serial-for-android.
 *
 * Retorna null si el sistema no soporta devpts.
 */
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_diamon_curso_core_PtyBridge_createPty(JNIEnv *env, jclass clazz) {
    // Abrir el master PTY
    int masterFd = posix_openpt(O_RDWR | O_NOCTTY);
    if (masterFd < 0) {
        LOGE("posix_openpt falló: errno=%d", errno);
        return nullptr;
    }
    if (grantpt(masterFd) != 0) {
        LOGE("grantpt falló: errno=%d", errno);
        close(masterFd);
        return nullptr;
    }
    if (unlockpt(masterFd) != 0) {
        LOGE("unlockpt falló: errno=%d", errno);
        close(masterFd);
        return nullptr;
    }
    const char *slavePath = ptsname(masterFd);
    if (slavePath == nullptr) {
        LOGE("ptsname falló: errno=%d", errno);
        close(masterFd);
        return nullptr;
    }

    // ── CRÍTICO: poner el PTY en modo RAW (binario puro) ──────────────
    // Sin esto, la line discipline del PTY transforma bytes del protocolo
    // serprog: 0x03 → SIGINT, 0x13 → XOFF (¡es S_CMD_O_SPIOP!),
    // 0x0D → convierte a 0x0A, etc.  cfmakeraw() desactiva TODO eso.
    struct termios tio;
    if (tcgetattr(masterFd, &tio) == 0) {
        cfmakeraw(&tio);
        tcsetattr(masterFd, TCSANOW, &tio);
        LOGI("PTY master configurado en modo RAW (binario puro)");
    } else {
        LOGE("tcgetattr falló en master: errno=%d (continuando)", errno);
    }
    // También configurar el slave para que flashrom lo vea en raw
    int slaveFd = open(slavePath, O_RDWR | O_NOCTTY);
    if (slaveFd >= 0) {
        if (tcgetattr(slaveFd, &tio) == 0) {
            cfmakeraw(&tio);
            tcsetattr(slaveFd, TCSANOW, &tio);
            LOGI("PTY slave configurado en modo RAW (FD guardado para mantener estado)");
        }
    }

    LOGI("PTY creado: master_fd=%d slave=%s slave_fd=%d", masterFd, slavePath, slaveFd);

    // Construir array de retorno: [masterFdAsString, slavePath, slaveFdAsString]
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(3, stringClass, nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF(std::to_string(masterFd).c_str()));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF(slavePath));
    env->SetObjectArrayElement(result, 2, env->NewStringUTF(std::to_string(slaveFd).c_str()));
    return result;
}

/**
 * Cierra un file descriptor nativo. Usado para liberar el master del PTY
 * cuando la operación de flashrom termina.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_diamon_curso_core_PtyBridge_closeFd(JNIEnv *env, jclass clazz, jint fd) {
    if (fd >= 0) {
        LOGI("Cerrando FD nativo: %d", fd);
        close((int) fd);
    }
}

/**
 * Escribe bytes directamente a un FD nativo (usado por USB->PTY).
 * Retorna cantidad escrita o -1 en error.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_diamon_curso_core_PtyBridge_writeFd(JNIEnv *env, jclass clazz, jint fd, jbyteArray data, jint len) {
    if (fd < 0 || data == nullptr || len <= 0) {
        return -1;
    }

    jsize arrLen = env->GetArrayLength(data);
    if (len > arrLen) {
        len = arrLen;
    }

    std::string buf;
    buf.resize(static_cast<size_t>(len));
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(&buf[0]));

    ssize_t w = write(fd, buf.data(), static_cast<size_t>(len));
    if (w < 0) {
        LOGE("writeFd falló: fd=%d len=%d errno=%d", (int) fd, (int) len, errno);
        return -1;
    }
    return static_cast<jint>(w);
}

/**
 * Test end-to-end: abre el slave PTY (como flashrom), envía SYNCNOP (0x10),
 * y espera la respuesta (0x15 0x06) a través de toda la cadena de forwarding:
 *   slave → master → Thread A → USB → Arduino → USB → Thread B → master → slave
 *
 * Usa poll() para timeout no-bloqueante de 2 segundos.
 * Retorna un string de diagnóstico legible.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_curso_core_PtyBridge_nativeTestRoundTrip(JNIEnv *env, jclass clazz, jstring jSlavePath) {
    const char *slavePath = env->GetStringUTFChars(jSlavePath, nullptr);

    // Abrir slave exactamente como flashrom: O_RDWR | O_NOCTTY
    int fd = open(slavePath, O_RDWR | O_NOCTTY);
    env->ReleaseStringUTFChars(jSlavePath, slavePath);

    if (fd < 0) {
        char msg[256];
        snprintf(msg, sizeof(msg), "[ERROR] No pude abrir slave PTY: errno=%d", errno);
        LOGE("testRoundTrip: %s", msg);
        return env->NewStringUTF(msg);
    }

    // Configurar raw mode (como hace flashrom en sp_openserport)
    struct termios tio;
    if (tcgetattr(fd, &tio) == 0) {
        cfmakeraw(&tio);
        tcsetattr(fd, TCSANOW, &tio);
    }

    // Escribir SYNCNOP (0x10) al slave → debería llegar al Arduino vía forwarding
    uint8_t cmd = 0x10;
    ssize_t written = write(fd, &cmd, 1);
    if (written != 1) {
        close(fd);
        char msg[256];
        snprintf(msg, sizeof(msg), "[ERROR] write() al slave PTY falló: written=%zd errno=%d", written, errno);
        LOGE("testRoundTrip: %s", msg);
        return env->NewStringUTF(msg);
    }
    LOGI("testRoundTrip: escribí 0x10 al slave PTY");

    // Esperar respuesta con poll() — timeout de 2 segundos
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;
    int ready = poll(&pfd, 1, 2000);

    char msg[512];
    if (ready < 0) {
        close(fd);
        snprintf(msg, sizeof(msg), "[ERROR] poll() falló: errno=%d", errno);
        LOGE("testRoundTrip: %s", msg);
        return env->NewStringUTF(msg);
    }
    if (ready == 0) {
        close(fd);
        snprintf(msg, sizeof(msg),
                 "[FALLO] Timeout 2s: el SYNCNOP salió por el PTY slave pero la respuesta "
                 "del Arduino NO llegó de vuelta. Los hilos de forwarding no mueven datos "
                 "correctamente (PTY→USB funciona, pero USB→PTY no entregan al slave).");
        LOGE("testRoundTrip: %s", msg);
        return env->NewStringUTF(msg);
    }

    // Leer respuesta
    uint8_t buf[32];
    ssize_t n = read(fd, buf, sizeof(buf));
    close(fd);

    if (n <= 0) {
        snprintf(msg, sizeof(msg), "[FALLO] poll() reportó datos pero read() devolvió %zd (errno=%d)", n, errno);
        LOGE("testRoundTrip: %s", msg);
        return env->NewStringUTF(msg);
    }

    // Formatear hex
    char hex[128] = {0};
    for (int i = 0; i < n && i < 32; i++) {
        char h[4];
        snprintf(h, sizeof(h), "%02X ", buf[i]);
        strcat(hex, h);
    }

    LOGI("testRoundTrip: recibido [%s] (%zd bytes)", hex, n);

    if (n >= 2 && buf[0] == 0x15 && buf[1] == 0x06) {
        snprintf(msg, sizeof(msg),
                 "[OK] Round-trip PTY completo: %s— cadena slave→USB→Arduino→USB→slave funciona perfectamente", hex);
    } else {
        snprintf(msg, sizeof(msg),
                 "[FALLO] Respuesta incorrecta del round-trip: %s(esperado: 15 06). "
                 "Los bytes se corrompen en el forwarding.", hex);
    }
    return env->NewStringUTF(msg);
}

#include <sys/wait.h>
#include <sys/types.h>
#include <signal.h>
#include <vector>

extern "C" JNIEXPORT jintArray JNICALL
Java_com_diamon_curso_core_FlashromExecutor_startNativeProcess(
        JNIEnv *env, jclass clazz,
        jstring jExecutable,
        jobjectArray jArgs,
        jint usbFd,
        jstring jLdLibraryPath,
        jstring jMiniproData,
        jstring jWorkingDir) {

    const char *execPath = env->GetStringUTFChars(jExecutable, nullptr);
    const char *ldLibPath = env->GetStringUTFChars(jLdLibraryPath, nullptr);
    const char *miniproData = env->GetStringUTFChars(jMiniproData, nullptr);
    const char *workingDir = env->GetStringUTFChars(jWorkingDir, nullptr);

    // Preparar argumentos (argv)
    int argc = env->GetArrayLength(jArgs);
    std::vector<char*> argv;
    std::vector<const char*> stringsToRelease;
    argv.push_back(const_cast<char*>(execPath)); // argv[0] debe ser el nombre del ejecutable
    for (int i = 0; i < argc; i++) {
        jstring argStr = (jstring) env->GetObjectArrayElement(jArgs, i);
        const char *arg = env->GetStringUTFChars(argStr, nullptr);
        argv.push_back(const_cast<char*>(arg));
        stringsToRelease.push_back(arg);
    }
    argv.push_back(nullptr);

    // Crear pipe para capturar stdout y stderr
    int pipefds[2];
    if (pipe(pipefds) < 0) {
        LOGE("startNativeProcess: pipe falló, errno=%d", errno);
        env->ReleaseStringUTFChars(jExecutable, execPath);
        env->ReleaseStringUTFChars(jLdLibraryPath, ldLibPath);
        env->ReleaseStringUTFChars(jMiniproData, miniproData);
        env->ReleaseStringUTFChars(jWorkingDir, workingDir);
        for (int i = 0; i < argc; i++) {
            jstring argStr = (jstring) env->GetObjectArrayElement(jArgs, i);
            env->ReleaseStringUTFChars(argStr, stringsToRelease[i]);
        }
        return nullptr;
    }

    pid_t pid = fork();
    if (pid == 0) {
        // --- PROCESO HIJO ---
        // Redirigir stdout y stderr al write-end del pipe
        dup2(pipefds[1], STDOUT_FILENO);
        dup2(pipefds[1], STDERR_FILENO);
        close(pipefds[0]);
        close(pipefds[1]);

        if (workingDir && strlen(workingDir) > 0) {
            chdir(workingDir);
        }

        // Configurar variables de entorno
        char fdStr[16];
        snprintf(fdStr, sizeof(fdStr), "%d", usbFd);
        if (usbFd >= 0) {
            setenv("ANDROID_USB_FD", fdStr, 1);
        }
        setenv("LD_LIBRARY_PATH", ldLibPath, 1);
        if (miniproData && strlen(miniproData) > 0) {
            setenv("MINIPRO_DATA", miniproData, 1);
        }

        // Remover O_CLOEXEC del FD de USB por si acaso
        if (usbFd >= 0) {
            int flags = fcntl(usbFd, F_GETFD);
            if (flags >= 0 && (flags & FD_CLOEXEC)) {
                fcntl(usbFd, F_SETFD, flags & ~FD_CLOEXEC);
            }
        }

        // Ejecutar el binario
        execv(execPath, argv.data());
        exit(127);
    }

    // --- PROCESO PADRE ---
    // Cerrar el write-end del pipe en el padre
    close(pipefds[1]);

    // Liberar memoria JNI
    env->ReleaseStringUTFChars(jExecutable, execPath);
    env->ReleaseStringUTFChars(jLdLibraryPath, ldLibPath);
    env->ReleaseStringUTFChars(jMiniproData, miniproData);
    env->ReleaseStringUTFChars(jWorkingDir, workingDir);
    for (int i = 0; i < argc; i++) {
        jstring argStr = (jstring) env->GetObjectArrayElement(jArgs, i);
        env->ReleaseStringUTFChars(argStr, stringsToRelease[i]);
    }

    jintArray result = env->NewIntArray(2);
    if (result != nullptr) {
        jint arr[2] = { (jint) pid, (jint) pipefds[0] };
        env->SetIntArrayRegion(result, 0, 2, arr);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_diamon_curso_core_FlashromExecutor_waitForNativeProcess(
        JNIEnv *env, jclass clazz, jint pid) {
    int status;
    if (waitpid((pid_t) pid, &status, 0) < 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_diamon_curso_core_FlashromExecutor_terminateNativeProcess(
        JNIEnv *env, jclass clazz, jint pid) {
    if (pid > 0) {
        kill((pid_t) pid, SIGKILL);
    }
}
