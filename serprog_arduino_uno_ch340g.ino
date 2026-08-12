#include <SPI.h>

// serprog status bytes
#define S_ACK 0x06
#define S_NAK 0x15

// Beacon consumido por PtyBridge antes de habilitar forwarding
#define BEACON_BYTE1 0xAA
#define BEACON_BYTE2 0x55

// Chip-select SPI (Arduino UNO)
#define SPI_CS_PIN 10

// Comando opcional de diagnóstico: vuelca los últimos comandos recibidos
#define CMD_DEBUG_DUMP 0xEE
#define DEBUG_BUF_SIZE 32

byte debugCmdBuffer[DEBUG_BUF_SIZE];
uint8_t debugCmdIndex = 0;

void handle_spi_op();
uint32_t read_fixed_size(int n);
void flush_serial_input();

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100);

  for (int i = 0; i < DEBUG_BUF_SIZE; i++) {
    debugCmdBuffer[i] = 0xFF;
  }

  // CH340/UNO suele reiniciar al abrir puerto: esperar estabilidad
  delay(2000);
  flush_serial_input();

  // Beacon de arranque para el host Android (PtyBridge)
  Serial.write(BEACON_BYTE1);
  Serial.write(BEACON_BYTE2);
  Serial.flush();

  SPI.begin();
  SPI.setClockDivider(SPI_CLOCK_DIV4); // ~4MHz en UNO
  SPI.setDataMode(SPI_MODE0);
  SPI.setBitOrder(MSBFIRST);

  pinMode(SPI_CS_PIN, OUTPUT);
  digitalWrite(SPI_CS_PIN, HIGH);

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);
}

void loop() {
  if (Serial.available() <= 0) {
    return;
  }

  digitalWrite(LED_BUILTIN, HIGH);
  byte cmd = (byte)Serial.read();

  debugCmdBuffer[debugCmdIndex] = cmd;
  debugCmdIndex = (debugCmdIndex + 1) % DEBUG_BUF_SIZE;

  switch (cmd) {
    case 0x00: // NOP
      Serial.write(S_ACK);
      Serial.flush();
      break;

    case 0x01: { // Query Interface Version v1.0
      uint8_t resp[3] = {S_ACK, 0x01, 0x00};
      Serial.write(resp, 3);
      Serial.flush();
      break;
    }

    case 0x02: { // Query Command Map (32 bytes)
      byte map[32] = {0};
      map[0] = 0x3F; // 0x00..0x05
      map[1] = 0x01; // 0x08 (0x01 << 0)
      map[2] = 0x0F; // 0x10, 0x11, 0x12, 0x13

      // ACK + 32 bytes del mapa como bloque atómico
      Serial.write(S_ACK);
      Serial.write(map, 32);
      Serial.flush();
      break;
    }

    case 0x03: { // Query Programmer Name (16 bytes)
      static const char name[16] = "arduino";
      Serial.write(S_ACK);
      Serial.write((const uint8_t*)name, 16);
      Serial.flush();
      break;
    }

    case 0x04: { // Query Serial Buffer Size
      uint8_t resp[3] = {S_ACK, 0x40, 0x00}; // LE -> 0x0040 (64 bytes)
      Serial.write(resp, 3);
      Serial.flush();
      break;
    }

    case 0x05: { // Query Supported Bustypes
      uint8_t resp[2] = {S_ACK, 0x08}; // SPI
      Serial.write(resp, 2);
      Serial.flush();
      break;
    }

    case 0x08: { // Query Maximum Write Length
      uint8_t resp[4] = {S_ACK, 32, 0x00, 0x00}; // 32 bytes
      Serial.write(resp, 4);
      Serial.flush();
      break;
    }

    case 0x11: { // Query Maximum Read Length
      uint8_t resp[4] = {S_ACK, 64, 0x00, 0x00}; // 64 bytes
      Serial.write(resp, 4);
      Serial.flush();
      break;
    }

    case 0x10: // SYNCNOP: NAK + ACK
      // No vaciar el UART aquí: flashrom puede haber enviado el siguiente
      // comando en el mismo paquete USB-serial. Descartarlo desincroniza el
      // flujo y hace que la respuesta siguiente parezca corrupta.
      {
        uint8_t resp[2] = {S_NAK, S_ACK};
        Serial.write(resp, 2);
        Serial.flush();
      }
      break;

    case 0x12: { // Set bus type
      byte bt = (byte)read_fixed_size(1);
      if (bt == 0x08) {
        Serial.write(S_ACK); // SPI soportado
      } else {
        Serial.write(S_NAK);
      }
      Serial.flush();
      break;
    }

    case 0x13: // SPI operation
      handle_spi_op();
      break;

    case CMD_DEBUG_DUMP: {
      // Formato: DD + 32 bytes + CC
      Serial.write(0xDD);
      Serial.write(debugCmdBuffer, DEBUG_BUF_SIZE);
      Serial.write(0xCC);
      Serial.flush();
      break;
    }

    default:
      Serial.write(S_NAK);
      Serial.flush();
      break;
  }

  digitalWrite(LED_BUILTIN, LOW);
}

void handle_spi_op() {
  uint32_t slen = read_fixed_size(3);
  uint32_t rlen = read_fixed_size(3);

  if (slen == 0xFFFFFFFF || rlen == 0xFFFFFFFF) {
    Serial.write(S_NAK);
    Serial.flush();
    flush_serial_input();
    return;
  }

  digitalWrite(SPI_CS_PIN, LOW);

  while (slen--) {
    unsigned long start = millis();
    while (Serial.available() == 0) {
      if (millis() - start > 1000) {
        digitalWrite(SPI_CS_PIN, HIGH);
        Serial.write(S_NAK);
        Serial.flush();
        return;
      }
    }
    SPI.transfer((uint8_t)Serial.read());
  }

  // El protocolo serprog exige:
  //   -> ACK (1 byte)
  //   -> rlen bytes de datos SPI leídos
  // Deben enviarse como flujo continuo. El ACK DEBE ir en el MISMO
  // bloque USB que los primeros bytes de datos para que flashrom no
  // lea un paquete USB vacío/parcial y pierda la sincronización.

  if (rlen == 0) {
    // Sin datos de lectura: sólo ACK
    Serial.write(S_ACK);
    Serial.flush();
  } else {
    // Leer del SPI y enviar ACK + datos en bloques de 32 bytes.
    // Usar bloques de 32 (no 64) para dejar margen en el buffer
    // hardware del ATmega328P (doble buffer de 1 byte) y evitar
    // que los retardos de vaciado del TX compitan con recepción.
    byte buffer[32];
    bool ackSent = false;
    while (rlen > 0) {
      uint32_t chunk;
      if (!ackSent) {
        // Primer bloque: ACK va como primer byte del buffer
        chunk = (rlen > 31) ? 31 : rlen;
        buffer[0] = S_ACK;
        for (uint32_t i = 0; i < chunk; i++) {
          buffer[i + 1] = SPI.transfer(0x00);
        }
        Serial.write(buffer, chunk + 1);
        Serial.flush();
        ackSent = true;
      } else {
        chunk = (rlen > 32) ? 32 : rlen;
        for (uint32_t i = 0; i < chunk; i++) {
          buffer[i] = SPI.transfer(0x00);
        }
        Serial.write(buffer, chunk);
        Serial.flush();
      }
      rlen -= chunk;
    }
  }

  digitalWrite(SPI_CS_PIN, HIGH);
}

uint32_t read_fixed_size(int n) {
  uint32_t val = 0;
  for (int i = 0; i < n; i++) {
    unsigned long start = millis();
    while (Serial.available() == 0) {
      if (millis() - start > 1000) {
        return 0xFFFFFFFF;
      }
    }
    val |= ((uint32_t)(uint8_t)Serial.read()) << (i * 8);
  }
  return val;
}

void flush_serial_input() {
  delay(10);
  while (Serial.available() > 0) {
    (void)Serial.read();
  }
}
