# Operation Zero

A bomb-defusal game for COE 421L (Fall 2026). A LilyPad Arduino "bomb" with sensors talks over **ZigBee (XBee)** to a **Java Swing** game running on a laptop. The player must complete **6 mini-games** before the 3-minute timer runs out. Each strike adds 5 seconds.

## How the system fits together

```
 Sensors ──> LilyPad (ATmega328P, 8 MHz) ──XBee "RX" ~~~ ZigBee ~~~ XBee "TX1" (Explorer) ──USB──> Laptop: Java game
 Buzzer  <──          firmware (.ino)                                                          Swing UI + game logic
```

- Board to game: sensor readings and key / card / button events.
- Game to board: commands, including tone requests for the buzzer.

## Hardware

| Part | Pin (LilyPad) |
| --- | --- |
| XBee serial RX / TX | D0 / D1 (TX through a 1 kΩ + 2 kΩ divider to 3.3 V) |
| Keypad rows | D4 to D7 |
| Keypad columns | D8 to D10 |
| RFID reader (Parallax) | D11 |
| Ultrasonic trigger / echo | D12 / D13 |
| Joystick X / Y | A0 / A1 |
| Accelerometer X / Y | A2 / A3 |
| DEFUSE button | A4 (internal pull-up) |
| Passive buzzer | A5 (`BUZZER_PIN` in the firmware) |

Peripherals: RFID, keypad, joystick, ultrasonic sensor, accelerometer, push button, passive buzzer (7).

## The 6 mini-games

| # | Name | Components | What the player does |
| --- | --- | --- | --- |
| 1 | Security Authorization | RFID | Answers a colour question by scanning the card of that colour |
| 2 | Detonator | joystick, buzzer | Repeats a sequence of arrows and tones with the joystick |
| 3 | Rhythm Lock | button, buzzer | Taps back a long/short rhythm on the DEFUSE button |
| 4 | Ghost Hunt | joystick, buzzer, screen | Eats 4 pellets in a Pac-Man maze while a ghost chases |
| 5 | Radar Lock | ultrasonic, buzzer | Finds a hidden distance; beeps speed up as the player gets closer |
| 6 | Override Code | keypad | Works out a code from a clue and types it |

A constant rule runs during the whole game: shaking the bomb is a strike (accelerometer, `StabilityMonitor`).

## Software

- **Language:** Java 8+ (Swing). Serial port library: [jSSC 2.9.6](https://github.com/java-native/jssc).
- **Design patterns:** Observer (`GameObserver`), Strategy (`SerialLink`, `BombModule`), Template Method (`HoldModule`), Singleton (`Sound`).
- **Threads:** serial reader thread, game worker thread (blocking queue), Swing event thread, sound thread.
- **UI:** 2-bit Pac-Man arcade style (black, blue, yellow, white), custom bitmap font.

### Source layout

```
src/        Java game (all classes in the default package)
firmware/   Arduino sketch for the LilyPad
lib/        put jssc-2.9.6.jar here
```

## Setup and run

### 1. Firmware
1. Open `firmware/OperationZero/OperationZero.ino` in the Arduino IDE.
2. Board: **LilyPad Arduino (ATmega328P, 8 MHz)**. Port: the FTDI on the LilyPad.
3. **Disconnect the XBee Tx wire from D0** before uploading, then reconnect it.
4. Upload. You should hear one short beep.

### 2. Java game
1. Create an Eclipse Java project, copy `src/` into it, and add `lib/jssc-2.9.6.jar` to the build path.
2. In `Main.java` set:
   - `PORT` to the XBee Explorer serial port (macOS example: `/dev/cu.usbserial-XXXXXXXX`)
   - `USE_SIMULATOR = true` to play without hardware
   - `USE_JOYSTICK`, `USE_ALIGNMENT` to switch the joystick or ultrasonic games on or off
   - `CARD_IDS` to the three RFID card IDs (red, blue, green)
3. Run `Main`. Enter a name and press START.

Close the Arduino Serial Monitor before running the game, since only one program can hold a serial port.

## Serial protocol (9600 baud, text lines)

| Direction | Messages |
| --- | --- |
| Game to board | `G` start streaming, `X` stop, `Z` calibrate, `P` ping, `D` defuse check, `N,<Hz>,<ms>` tone (0 stops) |
| Board to game | `RDY`, `OK,G`, `OK,X`, `PONG`, `S,enc,jx,jy,ax,ay,cm` every 100 ms, `K,<key>`, `B`, `R,<tag>` |

## Tuning

- Joystick direction wrong in Detonator or Ghost Hunt: flip `JX_SIGN` / `JY_SIGN` at the top of `DetonatorModule.java` and `MazeModule.java`.
- Keypad column 2 dead: set `Clues.DEAD_DIGITS = "2580"`.
- Time limit: `timeLimitMs` in `BombConfiguration.java` (and the idle display in `GameController.publish`).
- Accelerometer rule: see `StabilityMonitor` and its call in `GameController`.

## Photos

<img width="1600" height="1200" alt="game-connecting" src="https://github.com/user-attachments/assets/f24e3cda-afc5-4331-9ee2-8fa93ca412d8" />


<img width="1600" height="1200" alt="game-bomb-defused" src="https://github.com/user-attachments/assets/60882e9c-7c1b-4b6c-b16f-4bfed12a778a" />


<img width="1200" height="1600" alt="bomb-interior" src="https://github.com/user-attachments/assets/79bc2b85-5d39-4f1b-925d-a1a96a9761d9" />


<img width="1200" height="1600" alt="bomb-front-panel" src="https://github.com/user-attachments/assets/b058c60b-45eb-47bf-875c-ae3cfbd40c13" />


## Course

COE 421L Computer Systems Engineering Lab, American University of Sharjah, Fall 2026 (final project, Phase III).
Author: Abdoulrahim Ahmadou, Abdelhamyd Dawoud, Mostafa, and Ahmed.
