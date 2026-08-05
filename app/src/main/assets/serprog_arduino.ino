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

  delay(100);
  flush_serial_input();

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

    case 0x01: // Query Interface Version v1.0
      Serial.write(S_ACK);
      Serial.write(0x01);
      Serial.write(0x00);
      Serial.flush();
      break;

    case 0x02: { // Query Command Map (32 bytes)
      byte map[32] = {0};
      map[0] = 0x3F; // 0x00..0x05
      map[2] = 0x0D; // 0x10, 0x12 y 0x13

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

    case 0x04: // Query Serial Buffer Size
      Serial.write(S_ACK);
      Serial.write(0x00); // LE -> 0x0100
      Serial.write(0x01);
      Serial.flush();
      break;

    case 0x05: // Query Supported Bustypes
      Serial.write(S_ACK);
      Serial.write(0x08); // SPI
      Serial.flush();
      break;

    case 0x10: // SYNCNOP: NAK + ACK
      // Limpia basura vieja antes de responder SYNC
      flush_serial_input();
      Serial.write(S_NAK);
      Serial.write(S_ACK);
      Serial.flush();
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
      // Evitar que bytes residuales queden desfasando el parser
      flush_serial_input();
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

  Serial.write(S_ACK);
  Serial.flush();

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

  if (rlen > 0) {
    byte buffer[64];
    while (rlen > 0) {
      uint32_t chunk = (rlen > sizeof(buffer)) ? sizeof(buffer) : rlen;
      for (uint32_t i = 0; i < chunk; i++) {
        buffer[i] = SPI.transfer(0x00);
      }
      Serial.write(buffer, chunk);
      Serial.flush();
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
