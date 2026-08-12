#include <iostream>
#include <vector>
#include <string>
#include <cstring>
#include <algorithm>
#include <thread>
#include <mutex>
#include <atomic>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <cstdint>
#include <cstdlib>

// Op-codes del protocolo SPI para GD25Q80 (1MB)
#define CMD_WREN         0x06
#define CMD_WRDI         0x04
#define CMD_RDSR         0x05
#define CMD_WRSR         0x01
#define CMD_READ         0x03
#define CMD_FAST_READ    0x0B
#define CMD_PP           0x02
#define CMD_SE           0x20
#define CMD_BE32         0x52
#define CMD_BE64         0xD8
#define CMD_CE           0xC7
#define CMD_CE_ALT       0x60
#define CMD_RDID         0x9F
#define CMD_REMS         0x90
#define CMD_RES          0xAB

// Inversión de bits requerida para CH341A
uint8_t reverse_byte(uint8_t b) {
    b = ((b & 0xF0) >> 4) | ((b & 0x0F) << 4);
    b = ((b & 0xCC) >> 2) | ((b & 0x33) << 2);
    b = ((b & 0xAA) >> 1) | ((b & 0x55) << 1);
    return b;
}

// Clase para simular el chip de memoria flash GD25Q80
class SpiFlashGD25Q80 {
private:
    std::vector<uint8_t> memory;
    std::mutex mtx;
    bool write_enable;
    uint8_t status_reg;
    bool cs_active;

    enum SpiState {
        SPI_IDLE,
        SPI_CMD,
        SPI_ADDR1,
        SPI_ADDR2,
        SPI_ADDR3,
        SPI_DUMMY,
        SPI_DATA_OUT,
        SPI_DATA_IN
    } state;

    uint8_t cmd;
    uint32_t addr;
    uint32_t byte_count;

public:
    SpiFlashGD25Q80() : memory(1048576, 0xFF), write_enable(false), status_reg(0x00), cs_active(false), state(SPI_IDLE), cmd(0), addr(0), byte_count(0) {}

    void load(const std::string& path) {
        std::lock_guard<std::mutex> lock(mtx);
        FILE* f = fopen(path.c_str(), "rb");
        if (f) {
            size_t r = fread(memory.data(), 1, memory.size(), f);
            std::cout << "[FLASH] Cargados " << r << " bytes desde " << path << std::endl;
            fclose(f);
        } else {
            std::cout << "[WARN] No se pudo abrir " << path << " para lectura." << std::endl;
        }
    }

    void save(const std::string& path) {
        std::lock_guard<std::mutex> lock(mtx);
        FILE* f = fopen(path.c_str(), "wb");
        if (f) {
            size_t w = fwrite(memory.data(), 1, memory.size(), f);
            std::cout << "[FLASH] Guardados " << w << " bytes en " << path << std::endl;
            fclose(f);
        } else {
            std::cout << "[WARN] No se pudo abrir " << path << " para escritura." << std::endl;
        }
    }

    void fill(const std::string& pattern) {
        std::lock_guard<std::mutex> lock(mtx);
        if (pattern == "empty" || pattern == "0xff") {
            std::fill(memory.begin(), memory.end(), 0xFF);
        } else if (pattern == "count") {
            for (size_t i = 0; i < memory.size(); i++) {
                memory[i] = (uint8_t)(i & 0xFF);
            }
        } else if (pattern == "random") {
            srand(time(NULL));
            for (size_t i = 0; i < memory.size(); i++) {
                memory[i] = (uint8_t)(rand() & 0xFF);
            }
        }
        std::cout << "[FLASH] Memoria rellenada con patrón: " << pattern << std::endl;
    }

    void csAssert() {
        std::lock_guard<std::mutex> lock(mtx);
        cs_active = true;
        state = SPI_CMD;
    }

    void csDeassert() {
        std::lock_guard<std::mutex> lock(mtx);
        cs_active = false;
        state = SPI_IDLE;
    }

    uint8_t transfer(uint8_t mosi) {
        std::lock_guard<std::mutex> lock(mtx);
        uint8_t miso = 0xFF;

        switch (state) {
            case SPI_IDLE:
                state = SPI_CMD;
                // fallthrough
            case SPI_CMD:
                cmd = mosi;
                addr = 0;
                byte_count = 0;

                if (cmd == CMD_RDID) {
                    state = SPI_DATA_OUT;
                } else if (cmd == CMD_READ || cmd == CMD_FAST_READ || cmd == CMD_PP ||
                           cmd == CMD_SE || cmd == CMD_BE32 || cmd == CMD_BE64 ||
                           cmd == CMD_REMS || cmd == CMD_RES) {
                    state = SPI_ADDR1;
                } else if (cmd == CMD_RDSR) {
                    state = SPI_DATA_OUT;
                } else if (cmd == CMD_WRSR) {
                    state = SPI_DATA_IN;
                } else if (cmd == CMD_WREN) {
                    write_enable = true;
                    state = SPI_IDLE;
                } else if (cmd == CMD_WRDI) {
                    write_enable = false;
                    state = SPI_IDLE;
                } else if (cmd == CMD_CE || cmd == CMD_CE_ALT) {
                    if (write_enable) {
                        std::fill(memory.begin(), memory.end(), 0xFF);
                    }
                    state = SPI_IDLE;
                } else {
                    state = SPI_IDLE;
                }
                break;

            case SPI_ADDR1:
                addr = (addr << 8) | mosi;
                state = SPI_ADDR2;
                break;
            case SPI_ADDR2:
                addr = (addr << 8) | mosi;
                state = SPI_ADDR3;
                break;
            case SPI_ADDR3:
                addr = (addr << 8) | mosi;
                addr &= 0xFFFFFF; // 24-bit address

                if (cmd == CMD_READ) {
                    state = SPI_DATA_OUT;
                } else if (cmd == CMD_FAST_READ) {
                    state = SPI_DUMMY;
                } else if (cmd == CMD_PP) {
                    state = SPI_DATA_IN;
                } else if (cmd == CMD_SE) {
                    if (write_enable) {
                        uint32_t start = addr & ~0xFFF;
                        for (uint32_t i = 0; i < 4096 && (start + i) < memory.size(); i++) {
                            memory[start + i] = 0xFF;
                        }
                    }
                    state = SPI_IDLE;
                } else if (cmd == CMD_BE32) {
                    if (write_enable) {
                        uint32_t start = addr & ~0x7FFF;
                        for (uint32_t i = 0; i < 32768 && (start + i) < memory.size(); i++) {
                            memory[start + i] = 0xFF;
                        }
                    }
                    state = SPI_IDLE;
                } else if (cmd == CMD_BE64) {
                    if (write_enable) {
                        uint32_t start = addr & ~0xFFFF;
                        for (uint32_t i = 0; i < 65536 && (start + i) < memory.size(); i++) {
                            memory[start + i] = 0xFF;
                        }
                    }
                    state = SPI_IDLE;
                } else if (cmd == CMD_REMS || cmd == CMD_RES) {
                    state = SPI_DATA_OUT;
                } else {
                    state = SPI_IDLE;
                }
                break;

            case SPI_DUMMY:
                state = SPI_DATA_OUT;
                break;

            case SPI_DATA_OUT:
                if (cmd == CMD_RDID) {
                    if (byte_count == 0) miso = 0xC8;      // GigaDevice Manufacturer ID
                    else if (byte_count == 1) miso = 0x40; // Memory Type
                    else if (byte_count == 2) miso = 0x14; // Capacity (GD25Q80 = 1MB)
                    else miso = 0x00;
                    byte_count++;
                } else if (cmd == CMD_READ || cmd == CMD_FAST_READ) {
                    miso = memory[addr];
                    addr = (addr + 1) % memory.size();
                } else if (cmd == CMD_RDSR) {
                    miso = status_reg | (write_enable ? 0x02 : 0x00);
                } else if (cmd == CMD_REMS) {
                    if (byte_count % 2 == 0) miso = 0xC8;
                    else miso = 0x13;
                    byte_count++;
                } else if (cmd == CMD_RES) {
                    miso = 0x13;
                }
                break;

            case SPI_DATA_IN:
                if (cmd == CMD_PP) {
                    if (write_enable) {
                        uint32_t page_offset = addr & 0xFF;
                        uint32_t page_start = addr & ~0xFF;
                        memory[page_start + page_offset] &= mosi; // Page Program solo limpia bits
                        addr = page_start + ((page_offset + 1) & 0xFF);
                    }
                } else if (cmd == CMD_WRSR) {
                    status_reg = mosi;
                    state = SPI_IDLE;
                }
                break;
        }
        return miso;
    }
};

// Configuración de PTY y retorno del master_fd
int setup_pty(const std::string& symlink_path) {
    int master_fd = posix_openpt(O_RDWR | O_NOCTTY);
    if (master_fd < 0) {
        std::cerr << "Error posix_openpt: " << strerror(errno) << std::endl;
        return -1;
    }
    if (grantpt(master_fd) < 0 || unlockpt(master_fd) < 0) {
        std::cerr << "Error grantpt/unlockpt" << std::endl;
        close(master_fd);
        return -1;
    }

    char* slave_name = ptsname(master_fd);
    if (!slave_name) {
        std::cerr << "Error ptsname" << std::endl;
        close(master_fd);
        return -1;
    }

    // Configurar master a RAW
    struct termios tio;
    if (tcgetattr(master_fd, &tio) == 0) {
        cfmakeraw(&tio);
        tcsetattr(master_fd, TCSANOW, &tio);
    }

    unlink(symlink_path.c_str());
    if (symlink(slave_name, symlink_path.c_str()) < 0) {
        std::cerr << "Error creando symlink " << symlink_path << ": " << strerror(errno) << std::endl;
    }

    return master_fd;
}

// Función auxiliar para leer exactamente N bytes de un FD
bool read_exactly(int fd, uint8_t* buf, size_t n) {
    size_t total = 0;
    while (total < n) {
        fd_set rfds;
        FD_ZERO(&rfds);
        FD_SET(fd, &rfds);

        struct timeval tv = {5, 0}; // Timeout de 5s
        int s = select(fd + 1, &rfds, nullptr, nullptr, &tv);
        if (s <= 0) return false;

        ssize_t r = read(fd, buf + total, n - total);
        if (r <= 0) return false;
        total += r;
    }
    return true;
}

// Hilo del emulador Serprog
void serprog_thread(SpiFlashGD25Q80* flash, const std::string& symlink) {
    std::cout << "[SERPROG] Iniciando emulación en " << symlink << std::endl;

    while (true) {
        int master_fd = setup_pty(symlink);
        if (master_fd < 0) {
            std::this_thread::sleep_for(std::chrono::seconds(2));
            continue;
        }

        // Enviar beacon de arranque (AA 55)
        uint8_t beacon[2] = {0xAA, 0x55};
        write(master_fd, beacon, 2);

        std::cout << "[SERPROG] Puerto serie listo. Esperando comandos..." << std::endl;

        while (true) {
            uint8_t cmd;
            ssize_t r = read(master_fd, &cmd, 1);
            if (r <= 0) {
                std::cout << "[SERPROG] Cliente desconectado." << std::endl;
                break;
            }

            switch (cmd) {
                case 0x00: { // NOP
                    uint8_t resp = 0x06; // S_ACK
                    write(master_fd, &resp, 1);
                    break;
                }
                case 0x01: { // Query Interface Version
                    uint8_t resp[3] = {0x06, 0x01, 0x00}; // ACK + v1.0
                    write(master_fd, resp, 3);
                    break;
                }
                case 0x02: { // Query Command Map
                    uint8_t resp[33] = {0};
                    resp[0] = 0x06; // ACK
                    resp[1] = 0x3F; // 0x00..0x05 soportados
                    resp[3] = 0x0D; // 0x10, 0x12, 0x13 soportados
                    write(master_fd, resp, 33);
                    break;
                }
                case 0x03: { // Query Programmer Name
                    uint8_t resp[17] = {0};
                    resp[0] = 0x06;
                    memcpy(&resp[1], "arduino", 7);
                    write(master_fd, resp, 17);
                    break;
                }
                case 0x04: { // Query Serial Buffer Size
                    uint8_t resp[3] = {0x06, 0x00, 0x01}; // 256 bytes (0x0100)
                    write(master_fd, resp, 3);
                    break;
                }
                case 0x05: { // Query Supported Bustypes
                    uint8_t resp[2] = {0x06, 0x08}; // ACK + SPI
                    write(master_fd, resp, 2);
                    break;
                }
                case 0x10: { // SYNCNOP
                    uint8_t resp[2] = {0x15, 0x06}; // S_NAK + S_ACK
                    write(master_fd, resp, 2);
                    break;
                }
                case 0x12: { // Set Bus Type
                    uint8_t bt;
                    if (!read_exactly(master_fd, &bt, 1)) break;
                    uint8_t resp = (bt == 0x08) ? 0x06 : 0x15;
                    write(master_fd, &resp, 1);
                    break;
                }
                case 0x13: { // SPI Operation
                    uint8_t len_bytes[6];
                    if (!read_exactly(master_fd, len_bytes, 6)) break;
                    uint32_t slen = len_bytes[0] | (len_bytes[1] << 8) | (len_bytes[2] << 16);
                    uint32_t rlen = len_bytes[3] | (len_bytes[4] << 8) | (len_bytes[5] << 16);

                    flash->csAssert();

                    // Leer slen bytes, transferir
                    std::vector<uint8_t> sbuf(slen);
                    if (slen > 0) {
                        if (!read_exactly(master_fd, sbuf.data(), slen)) {
                            flash->csDeassert();
                            break;
                        }
                        for (uint32_t i = 0; i < slen; i++) {
                            flash->transfer(sbuf[i]);
                        }
                    }

                    // Enviar ACK de escritura completada
                    uint8_t ack = 0x06;
                    write(master_fd, &ack, 1);

                    // Si hay rlen, transferir dummy y enviar de vuelta
                    if (rlen > 0) {
                        std::vector<uint8_t> rbuf(rlen);
                        for (uint32_t i = 0; i < rlen; i++) {
                            rbuf[i] = flash->transfer(0x00);
                        }
                        write(master_fd, rbuf.data(), rlen);
                    }

                    flash->csDeassert();
                    break;
                }
                default: {
                    uint8_t resp = 0x15; // NAK
                    write(master_fd, &resp, 1);
                    break;
                }
            }
        }
        close(master_fd);
    }
}

// Hilo del emulador Bus Pirate
void buspirate_thread(SpiFlashGD25Q80* flash, const std::string& symlink) {
    std::cout << "[BUSPIRATE] Iniciando emulación en " << symlink << std::endl;

    while (true) {
        int master_fd = setup_pty(symlink);
        if (master_fd < 0) {
            std::this_thread::sleep_for(std::chrono::seconds(2));
            continue;
        }

        std::cout << "[BUSPIRATE] Puerto listo en modo texto..." << std::endl;

        enum Mode { TEXT, BBIO, SPI } mode = TEXT;
        int zero_count = 0;

        while (true) {
            uint8_t b;
            ssize_t r = read(master_fd, &b, 1);
            if (r <= 0) break;

            if (mode == TEXT) {
                if (b == '\r' || b == '\n') {
                    write(master_fd, "\r\nHiZ>", 7);
                } else if (b == '#') {
                    write(master_fd, "\r\nRESET\r\nHiZ>", 13);
                } else if (b == 0x00) {
                    zero_count++;
                    if (zero_count >= 20) {
                        mode = BBIO;
                        write(master_fd, "BBIO1", 5);
                        zero_count = 0;
                        std::cout << "[BUSPIRATE] Entró a modo binario (BBIO)" << std::endl;
                    }
                } else {
                    zero_count = 0;
                }
            } else if (mode == BBIO) {
                if (b == 0x01) {
                    mode = SPI;
                    write(master_fd, "SPI1", 4);
                    std::cout << "[BUSPIRATE] Entró a modo SPI" << std::endl;
                } else if (b == 0x00) {
                    write(master_fd, "BBIO1", 5);
                } else if (b == 0x0f) {
                    mode = TEXT;
                    write(master_fd, "Bus Pirate v3a Firmware v5.5 HiZ>", 33);
                    std::cout << "[BUSPIRATE] Reset a modo texto" << std::endl;
                } else {
                    write(master_fd, "\x00", 1);
                }
            } else if (mode == SPI) {
                if (b == 0x00) {
                    mode = BBIO;
                    write(master_fd, "BBIO1", 5);
                    std::cout << "[BUSPIRATE] Volvió a modo BBIO" << std::endl;
                } else if (b == 0x01) {
                    write(master_fd, "SPI1", 4);
                } else if (b == 0x02) { // CS Low
                    flash->csAssert();
                    write(master_fd, "\x01", 1);
                } else if (b == 0x03) { // CS High
                    flash->csDeassert();
                    write(master_fd, "\x01", 1);
                } else if ((b & 0xF0) == 0x40) { // Configure peripherals
                    write(master_fd, "\x01", 1);
                } else if ((b & 0xF0) == 0x60) { // Configure speed
                    write(master_fd, "\x01", 1);
                } else if ((b & 0xF0) == 0x80) { // Configure SPI options
                    write(master_fd, "\x01", 1);
                } else if ((b & 0xF0) == 0x10) { // Bulk SPI transfer
                    uint8_t len = (b & 0x0F) + 1;
                    std::vector<uint8_t> buf(len);
                    if (read_exactly(master_fd, buf.data(), len)) {
                        for (int i = 0; i < len; i++) {
                            buf[i] = flash->transfer(buf[i]);
                        }
                        write(master_fd, buf.data(), len);
                    }
                } else if (b == 0x04) { // Write-then-read
                    uint8_t len_bytes[4];
                    if (read_exactly(master_fd, len_bytes, 4)) {
                        uint16_t wlen = (len_bytes[0] << 8) | len_bytes[1];
                        uint16_t rlen = (len_bytes[2] << 8) | len_bytes[3];

                        std::vector<uint8_t> wbuf(wlen);
                        if (read_exactly(master_fd, wbuf.data(), wlen)) {
                            flash->csAssert();
                            for (uint16_t i = 0; i < wlen; i++) {
                                flash->transfer(wbuf[i]);
                            }

                            // Responder con ACK (0x01)
                            write(master_fd, "\x01", 1);

                            // Responder con los bytes leídos
                            std::vector<uint8_t> rbuf(rlen);
                            for (uint16_t i = 0; i < rlen; i++) {
                                rbuf[i] = flash->transfer(0x00);
                            }
                            write(master_fd, rbuf.data(), rlen);
                            flash->csDeassert();
                        }
                    }
                } else {
                    write(master_fd, "\x00", 1);
                }
            }
        }
        close(master_fd);
    }
}

// Lógica de emulación CH341A para un socket/FD determinado
void handle_ch341a_client(SpiFlashGD25Q80* flash, int fd) {
    std::cout << "[CH341A-DEBUG] Cliente conectado al descriptor de socket/archivo " << fd << std::endl;

    // Enviar descriptor USB de 18 bytes al inicio para que libusb lo lea
    uint8_t ch341a_descriptor[18] = {
        18, 1, 0x10, 0x01, 0xFF, 0x00, 0x00, 64,
        0x86, 0x1a, 0x12, 0x55, 0x00, 0x01,
        1, 2, 0, 1
    };
    if (write(fd, ch341a_descriptor, 18) != 18) {
        std::cerr << "[CH341A] Error al escribir descriptor" << std::endl;
    }

    while (true) {
        uint32_t packet_len = 0;
        if (!read_exactly(fd, (uint8_t*)&packet_len, 4)) {
            std::cout << "[CH341A-DEBUG] EOF leyendo packet_len" << std::endl;
            break;
        }
        std::cout << "[CH341A-DEBUG] Recibido packet_len = " << packet_len << std::endl;

        std::vector<uint8_t> buf(packet_len);
        if (!read_exactly(fd, buf.data(), packet_len)) {
            std::cout << "[CH341A-DEBUG] EOF leyendo datos de longitud " << packet_len << std::endl;
            break;
        }

        uint32_t idx = 0;
        while (idx < packet_len) {
            uint8_t cmd = buf[idx++];
            std::cout << "[CH341A-DEBUG] Procesando comando: 0x" << std::hex << (int)cmd << std::dec << std::endl;
            if (cmd == 0xAB) { // CH341A_CMD_UIO_STREAM
                while (idx < packet_len) {
                    uint8_t op = buf[idx++];
                    if (op == 0x20) { // CH341A_CMD_UIO_STM_END
                        std::cout << "[CH341A-DEBUG] UIO_STM_END" << std::endl;
                        break;
                    } else if ((op & 0xC0) == 0x80) { // CH341A_CMD_UIO_STM_OUT
                        uint8_t pins = op & 0x3F;
                        std::cout << "[CH341A-DEBUG] UIO_STM_OUT pins: 0x" << std::hex << (int)pins << std::dec << std::endl;
                        if ((pins & 0x01) == 0) {
                            flash->csAssert();
                        } else {
                            flash->csDeassert();
                        }
                    } else if ((op & 0xC0) == 0x40) { // CH341A_CMD_UIO_STM_DIR
                        // Ignorar
                    }
                }
            } else if (cmd == 0xA8) { // CH341A_CMD_SPI_STREAM
                uint32_t spi_len = packet_len - idx;
                std::cout << "[CH341A-DEBUG] SPI_STREAM, longitud = " << spi_len << std::endl;
                std::vector<uint8_t> resp_buf(spi_len);
                for (uint32_t i = 0; i < spi_len; i++) {
                    uint8_t mosi = reverse_byte(buf[idx++]);
                    uint8_t miso = flash->transfer(mosi);
                    resp_buf[i] = reverse_byte(miso);
                }
                std::cout << "[CH341A-DEBUG] Escribiendo respuesta SPI, longitud = " << spi_len << std::endl;
                write(fd, resp_buf.data(), spi_len);
            } else if (cmd == 0xAA) { // CH341A_CMD_I2C_STREAM
                std::cout << "[CH341A-DEBUG] I2C_STREAM" << std::endl;
                while (idx < packet_len) {
                    uint8_t op = buf[idx++];
                    if (op == 0x00) break; // CH341A_CMD_I2C_STM_END
                }
            }
        }
    }
exit_client:
    std::cout << "[CH341A] Cliente desconectado." << std::endl;
}

// Servidor CH341A en UNIX Socket para modo background
void ch341a_socket_server(SpiFlashGD25Q80* flash, const std::string& path) {
    unlink(path.c_str());
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        std::cerr << "[CH341A] Error socket UNIX" << std::endl;
        return;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, path.c_str(), sizeof(addr.sun_path)-1);

    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        std::cerr << "[CH341A] Error bind UNIX socket" << std::endl;
        close(server_fd);
        return;
    }

    if (listen(server_fd, 5) < 0) {
        std::cerr << "[CH341A] Error listen UNIX socket" << std::endl;
        close(server_fd);
        return;
    }

    std::cout << "[CH341A] Servidor UNIX escuchando en " << path << std::endl;

    while (true) {
        int client_fd = accept(server_fd, nullptr, nullptr);
        if (client_fd >= 0) {
            handle_ch341a_client(flash, client_fd);
            close(client_fd);
        }
    }
    close(server_fd);
}

int main(int argc, char* argv[]) {
    bool run_ch341a = false;
    bool run_serprog = false;
    bool run_buspirate = false;
    bool run_all = false;
    std::string flash_file = "";
    std::string save_file = "";
    std::string fill_pattern = "";

    // Parsear argumentos
    for (int i = 1; i < argc; i++) {
        std::string arg = argv[i];
        if (arg == "--ch341a") run_ch341a = true;
        else if (arg == "--serprog") run_serprog = true;
        else if (arg == "--buspirate") run_buspirate = true;
        else if (arg == "--all") run_all = true;
        else if (arg == "--flash" && i + 1 < argc) flash_file = argv[++i];
        else if (arg == "--save" && i + 1 < argc) save_file = argv[++i];
        else if (arg == "--fill" && i + 1 < argc) fill_pattern = argv[++i];
        else if (arg == "-h" || arg == "--help") {
            std::cout << "Uso: emulador_flashrom [OPCIONES]\n"
                      << "  --ch341a         Emula programador CH341A USB SPI\n"
                      << "  --serprog        Emula Serprog Arduino via PTY\n"
                      << "  --buspirate      Emula Bus Pirate v3 SPI via PTY\n"
                      << "  --all            Ejecuta todos los emuladores simultáneamente (por defecto)\n"
                      << "  --flash ARCHIVO  Carga contenido de flash desde ARCHIVO al inicio\n"
                      << "  --save ARCHIVO   Guarda contenido de flash en ARCHIVO al salir\n"
                      << "  --fill PATRON    Rellena flash: empty (0xFF), count (0..255), random\n";
            return 0;
        }
    }

    // Por defecto si no se especifica modo, ejecutar todos
    if (!run_ch341a && !run_serprog && !run_buspirate && !run_all) {
        run_all = true;
    }

    SpiFlashGD25Q80 flash;

    if (!fill_pattern.empty()) {
        flash.fill(fill_pattern);
    }
    if (!flash_file.empty()) {
        flash.load(flash_file);
    }

    // Escribir el descriptor mock_usb.bin de CH341A (18 bytes) en el directorio de trabajo
    uint8_t ch341a_descriptor[18] = {
        18, 1, 0x10, 0x01, 0xFF, 0x00, 0x00, 64,
        0x86, 0x1a, 0x12, 0x55, 0x00, 0x01,
        1, 2, 0, 1
    };
    FILE* f_desc = fopen("mock_usb.bin", "wb");
    if (f_desc) {
        fwrite(ch341a_descriptor, 1, 18, f_desc);
        fclose(f_desc);
        std::cout << "[CH341A] Descriptor mock_usb.bin creado." << std::endl;
    }

    int flashrom_arg_idx = -1;
    for (int i = 1; i < argc; i++) {
        if (strstr(argv[i], "flashrom") != nullptr) {
            flashrom_arg_idx = i;
            break;
        }
    }

    if (run_ch341a && flashrom_arg_idx != -1) {
        std::cout << "[CH341A] Modo auto-contenido detectado. Lanzando subproceso..." << std::endl;
        int sv[2];
        if (socketpair(AF_UNIX, SOCK_STREAM, 0, sv) < 0) {
            std::cerr << "Error socketpair: " << strerror(errno) << std::endl;
            return 1;
        }

        pid_t pid = fork();
        if (pid == 0) {
            // Proceso hijo (ejecutará flashrom)
            close(sv[0]);
            dup2(sv[1], 99); // Duplicar socket a FD 99
            close(sv[1]);

            setenv("ANDROID_USB_FD", "99", 1);
            
            // Construir argv para execvp
            std::vector<char*> child_argv;
            for (int i = flashrom_arg_idx; i < argc; i++) {
                child_argv.push_back(argv[i]);
            }
            child_argv.push_back(nullptr);

            execvp(child_argv[0], child_argv.data());
            std::cerr << "Error en execvp: " << strerror(errno) << std::endl;
            exit(1);
        } else if (pid > 0) {
            // Proceso padre (corre emulador CH341A)
            close(sv[1]);
            handle_ch341a_client(&flash, sv[0]);
            close(sv[0]);

            int status;
            waitpid(pid, &status, 0);
            std::cout << "[CH341A] Subproceso flashrom finalizado." << std::endl;

            if (!save_file.empty()) {
                flash.save(save_file);
            }
            return 0;
        }
    }

    // Modo normal de background / hilos
    std::vector<std::thread> threads;

    if (run_all || run_serprog) {
        threads.push_back(std::thread(serprog_thread, &flash, "./serprog_pty"));
    }
    if (run_all || run_buspirate) {
        threads.push_back(std::thread(buspirate_thread, &flash, "./buspirate_pty"));
    }
    if (run_all || run_ch341a) {
        threads.push_back(std::thread(ch341a_socket_server, &flash, "./ch341a_socket"));
    }

    std::cout << "Emuladores iniciados. Presiona Ctrl+C para salir." << std::endl;

    for (auto& th : threads) {
        if (th.joinable()) th.join();
    }

    if (!save_file.empty()) {
        flash.save(save_file);
    }

    return 0;
}
