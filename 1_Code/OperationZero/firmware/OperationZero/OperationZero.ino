/*
 * Operation Zero - LilyPad (ATmega328P, 8 MHz) bomb station.
 *
 * The board reads the keypad, RFID reader, joystick, accelerometer, ultrasonic sensor
 * and the DEFUSE button, and drives a passive buzzer. It never decides anything about
 * the game: it only reports what the player does and plays the sounds the Java game
 * asks for. All messages travel over ZigBee (XBee) as lines of text at 9600 baud
 * on the hardware serial port (D0 = RX, D1 = TX).
 *
 * Protocol with the Java game:
//   Java -> board : G (start streaming) X (stop) Z (zero encoder) P (ping) D (debug keypad on/off)
//                   N,<Hz>,<ms>  play a note on the buzzer (N,0,0 = silence)
//   board -> Java : RDY  OK,G  OK,X  PONG
//                   S,enc,joyX,joyY,accX,accY,cm   (every 100 ms while streaming)
//                   K,<key>   B   R,<tag>          (only while streaming)
 */

#include <SoftwareSerial.h>   // second serial port, used only to listen to the RFID reader

// ---------- pins ----------
// rotary encoder (left over from an early design, no encoder is fitted on the final bomb)
const int ENC_CLK = 2;            // INT0
const int ENC_DT  = 3;
const int BUZZER_PIN = A5;        // passive buzzer (A5 is free: the RFID transmit pin is never used)
const int ROW_PINS[4] = {4, 5, 6, 7};   // R1..R4  (reorder here if the keypad reads wrong)
const int COL_PINS[3] = {8, 9, 10};     // C1..C3
const int RFID_RX = 11;                 // Parallax SOUT
const int RFID_TX = A5;                 // unused (SoftwareSerial needs a pin)
const int TRIG_PIN = 12;
const int ECHO_PIN = 13;
const int JOY_X = A0;
const int JOY_Y = A1;
const int ACC_X = A2;
const int ACC_Y = A3;
const int BTN_PIN = A4;

// key printed at each row/column crossing of the keypad
const char KEYS[4][3] = { {'1','2','3'}, {'4','5','6'}, {'7','8','9'}, {'*','0','#'} };

SoftwareSerial rfid(RFID_RX, RFID_TX);   // the Parallax reader talks at 2400 baud

// ---------- state ----------
volatile long enc = 0;                    // encoder position (changed inside the interrupt)
volatile unsigned long lastEncMicros = 0;
bool streaming = false;                   // true after the game sends G; sensor lines are sent only then
bool debugKeys = false;                   // D command: print extra keypad/RFID debug lines

String cmd = "";                          // command line being received from Java

// keypad (debounce state)
char lastRawKey = 0, reportedKey = 0;
unsigned long keyChangeMs = 0;
int keyRow = -1, keyCol = -1;

// button (debounce state)
int lastBtn = HIGH;
unsigned long btnChangeMs = 0;
int stableBtn = HIGH;

// rfid (a tag arrives as 0x0A, 10 ASCII characters, 0x0D)
String tagBuf = "";
bool tagReading = false;
String lastTag = "";
unsigned long lastTagMs = 0;

unsigned long lastSampleMs = 0;           // time of the last sensor line sent

// ---------- encoder interrupt ----------
// runs on every falling edge of CLK: the level of DT tells the direction
void encoderISR() {
  unsigned long now = micros();
  if (now - lastEncMicros < 2000) return;       // debounce
  lastEncMicros = now;
  if (digitalRead(ENC_DT)) enc++;
  else enc--;
}

// runs once at power-up
void setup() {
  Serial.begin(9600);       // link to the XBee
  rfid.begin(2400);

  // encoder clock with an interrupt so no pulse is missed
  pinMode(ENC_CLK, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(ENC_CLK), encoderISR, FALLING);

  // keypad: columns are inputs with pull-ups, rows are driven one at a time while scanning
  for (int c = 0; c < 3; c++) pinMode(COL_PINS[c], INPUT_PULLUP);
  for (int r = 0; r < 4; r++) pinMode(ROW_PINS[r], INPUT);   // high impedance until scanned

  pinMode(BTN_PIN, INPUT_PULLUP);           // button wired to ground: pressed = LOW
  pinMode(BUZZER_PIN, OUTPUT);
  tone(BUZZER_PIN, 1200, 120);              // short beep at power-up
  // ultrasonic sensor: TRIG is an output, ECHO an input
  pinMode(TRIG_PIN, OUTPUT);
  digitalWrite(TRIG_PIN, LOW);
  pinMode(ECHO_PIN, INPUT);

  Serial.println("RDY");                    // tells the game the board has (re)started
}

// ---------- keypad ----------
// Scans the matrix: pull one row LOW at a time and look for a column that goes LOW.
// Returns the key found, or 0 if none is pressed.
char scanKeypad() {
  char found = 0;
  keyRow = -1;
  keyCol = -1;
  for (int r = 0; r < 4; r++) {
    pinMode(ROW_PINS[r], OUTPUT);
    digitalWrite(ROW_PINS[r], LOW);
    delayMicroseconds(30);
    for (int c = 0; c < 3; c++) {
      if (digitalRead(COL_PINS[c]) == LOW && found == 0) {
        found = KEYS[r][c];
        keyRow = r;
        keyCol = c;
      }
    }
    pinMode(ROW_PINS[r], INPUT);
  }
  return found;
}

// Debounces the keypad: a key is reported once, after it has been stable for 40 ms.
void handleKeypad() {
  char k = scanKeypad();
  unsigned long now = millis();
  if (k != lastRawKey) {
    lastRawKey = k;
    keyChangeMs = now;
  } else if (now - keyChangeMs > 40 && k != reportedKey) {
    reportedKey = k;
    if (k != 0) {
      if (streaming) { Serial.print("K,"); Serial.println(k); }
      if (debugKeys) {
        Serial.print("DBG key=");
        Serial.print(k);
        Serial.print(" row=");
        Serial.print(keyRow + 1);
        Serial.print(" col=");
        Serial.println(keyCol + 1);
      }
    }
  }
}

// ---------- button ----------
// Debounces the DEFUSE button (50 ms) and reports one "B" per press.
void handleButton() {
  int b = digitalRead(BTN_PIN);
  unsigned long now = millis();
  if (b != lastBtn) {
    lastBtn = b;
    btnChangeMs = now;
  } else if (now - btnChangeMs > 50 && b != stableBtn) {
    stableBtn = b;
    if (b == LOW && streaming) Serial.println("B");
  }
}

// ---------- RFID (Parallax: 0x0A, 10 ASCII chars, 0x0D) ----------
// Collects the bytes of one tag and reports it as "R,<tag>".
// The same card held on the reader is reported only once per 600 ms.
void handleRFID() {
  while (rfid.available()) {
    int ch = rfid.read();
    if (ch == 0x0A) {
      tagBuf = "";
      tagReading = true;
    } else if (ch == 0x0D) {
      if (tagReading && tagBuf.length() == 10) {
        unsigned long now = millis();
        if (tagBuf != lastTag || now - lastTagMs > 600) {
          if (streaming || debugKeys) { Serial.print("R,"); Serial.println(tagBuf); }
          lastTag = tagBuf;
        }
        lastTagMs = now;
      }
      tagReading = false;
    } else if (tagReading) {
      if (tagBuf.length() < 12) tagBuf += (char)ch;
    }
  }
}

// ---------- ultrasonic ----------
// Sends a 10 us trigger pulse and times the echo. 58 us of echo = 1 cm.
int readCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(3);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  unsigned long us = pulseIn(ECHO_PIN, HIGH, 12000);
  return (int)(us / 58);          // 0 = no echo
}

// ---------- commands from Java ----------
// Runs one complete command line received from the game.
void handleCommand(String c) {
  c.trim();
  if (c == "G") {
    noInterrupts(); enc = 0; interrupts();
    streaming = true;
    Serial.println("OK,G");
  } else if (c == "X") {
    streaming = false;
    Serial.println("OK,X");
  } else if (c == "Z") {
    noInterrupts(); enc = 0; interrupts();
  } else if (c == "P") {
    Serial.println("PONG");
  } else if (c == "D") {
    debugKeys = !debugKeys;
    Serial.println(debugKeys ? "DBG on" : "DBG off");
  } else if (c.startsWith("N,")) {          // N,<Hz>,<ms> : one note on the buzzer
    int comma = c.indexOf(',', 2);
    if (comma > 0) {
      int hz = c.substring(2, comma).toInt();
      int ms = c.substring(comma + 1).toInt();
      if (hz > 0 && ms > 0) tone(BUZZER_PIN, hz, ms);
      else noTone(BUZZER_PIN);
    }
  }
}

// Reads incoming characters and splits them into lines (the game ends every command with a newline).
void handleSerial() {
  while (Serial.available()) {
    char ch = (char)Serial.read();
    if (ch == '\n') {
      handleCommand(cmd);
      cmd = "";
    } else if (ch != '\r') {
      if (cmd.length() < 16) cmd += ch;
    }
  }
}

// The main loop never blocks for long: it checks every input, then sends a sample every 100 ms.
void loop() {
  handleSerial();
  handleKeypad();
  handleButton();
  handleRFID();

  unsigned long now = millis();
  // while streaming, send one line with all analog sensors and the distance every 100 ms
  if (streaming && now - lastSampleMs >= 100) {
    lastSampleMs = now;
    long e;
    noInterrupts(); e = enc; interrupts();
    int cm = readCm();
    Serial.print("S,");
    Serial.print(e);            Serial.print(",");
    Serial.print(analogRead(JOY_X)); Serial.print(",");
    Serial.print(analogRead(JOY_Y)); Serial.print(",");
    Serial.print(analogRead(ACC_X)); Serial.print(",");
    Serial.print(analogRead(ACC_Y)); Serial.print(",");
    Serial.println(cm);
  }
}
