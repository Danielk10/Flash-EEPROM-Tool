# Makefile
CXX = g++
CXXFLAGS = -std=c++11 -Wall -pthread -O2
TARGET = emulador_flashrom
SRCS = emulador_flashrom.cpp

all: $(TARGET)

$(TARGET): $(SRCS)
	$(CXX) $(CXXFLAGS) -o $(TARGET) $(SRCS)

clean:
	rm -f $(TARGET) mock_usb.bin serprog_pty buspirate_pty
