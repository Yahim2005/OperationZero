/**
 * One message received from the LilyPad, already decoded.
 *
 * Line formats sent by the firmware (9600 baud, one per line):
 *   S,enc,jx,jy,ax,ay,cm   sensor sample every 100 ms (encoder, joystick, accelerometer, distance)
 *   K,<key>                keypad key pressed
 *   B                      DEFUSE button pressed
 *   R,<tag>                RFID card scanned
 *   OK,...                 acknowledgement of a command
 *   RDY                    board has started
 */
public class SensorEvent {

    public static final char SAMPLE = 'S';
    public static final char KEY = 'K';
    public static final char BUTTON = 'B';
    public static final char TAG = 'R';
    public static final char ACK = 'A';
    public static final char READY = 'D';
    public static final char OTHER = '?';

    public final char type;
    public final int enc, jx, jy, ax, ay, cm;
    public final String text;      // key character or RFID tag ID
    public final long time;

    private SensorEvent(char type, int[] v, String text) {
        this.type = type;
        this.enc = v[0];
        this.jx = v[1];
        this.jy = v[2];
        this.ax = v[3];
        this.ay = v[4];
        this.cm = v[5];
        this.text = text;
        this.time = System.currentTimeMillis();   // when the event arrived
    }

    // Turns one line from the LilyPad into an event
    public static SensorEvent parse(String line) {
        int[] none = new int[6];
        if (line == null) return new SensorEvent(OTHER, none, "");
        String[] p = line.trim().split(",");
        try {
            // sample: "S" plus six numbers
            if (p[0].equals("S") && p.length == 7) {
                int[] v = new int[6];
                for (int i = 0; i < 6; i++) v[i] = Integer.parseInt(p[i + 1].trim());
                return new SensorEvent(SAMPLE, v, "");
            }
            if (p[0].equals("K") && p.length == 2 && p[1].length() > 0)
                return new SensorEvent(KEY, none, p[1].substring(0, 1));
            if (p[0].equals("B"))
                return new SensorEvent(BUTTON, none, "");
            if (p[0].equals("R") && p.length == 2)
                return new SensorEvent(TAG, none, p[1].trim());
            if (p[0].equals("OK"))
                return new SensorEvent(ACK, none, "");
            if (p[0].equals("RDY"))
                return new SensorEvent(READY, none, "");
        } catch (NumberFormatException e) {
            // a damaged line (radio noise): fall through and report it as OTHER
        }
        return new SensorEvent(OTHER, none, line);
    }
}
