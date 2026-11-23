import javax.swing.SwingUtilities;

/**
 * Entry point of Operation Zero.
 * It picks the link (real ZigBee or simulator), loads the leaderboard, creates the
 * GameController and opens the window. Change the constants below to match your setup.
 */
public class Main {

    // true = fake bomb on screen, false = the real LilyPad over ZigBee
    static final boolean USE_SIMULATOR = false;
    // serial port of the XBee Explorer plugged into the laptop (macOS example)
    static final String PORT = "/dev/cu.usbserial-DN02SXA8";

    // the three RFID tag IDs read from the cards: index 0 = RED, 1 = BLUE, 2 = GREEN
    static final String[] CARD_IDS = {"4300BB705E", "25005FF44F", "2600D610B3"};

    // switches for the mini-games that need extra wiring
    static final boolean USE_JOYSTICK = true;    // Detonator and Ghost Hunt
    static final boolean USE_ALIGNMENT = false;  // Radar Lock (ultrasonic on D12/D13, so the buzzer must not use D12)

    public static void main(String[] args) {
        // Strategy: the controller works the same with either link
        SerialLink link = USE_SIMULATOR ? new SimLink(CARD_IDS) : new HardwareLink(PORT);
        Leaderboard leaderboard = new Leaderboard("leaderboard.dat");
        final GameController controller =
                new GameController(link, CARD_IDS, USE_JOYSTICK, USE_ALIGNMENT, leaderboard);
        // Swing windows must be created on the event thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                GameView view = new GameView(controller);
                controller.addObserver(view);   // Observer: the view follows the game
                view.setVisible(true);
                controller.refresh();
            }
        });
        // start the reader and worker threads
        controller.start();
    }
}
