/**
 * Mini-game 2, "Detonator" (joystick + buzzer): a memory game.
 * The detonator flashes an arrow and plays a tone for each step of a secret sequence.
 * The player repeats the sequence with the joystick. A mistake is a strike and the
 * sequence is shown again.
 * Works as a small state machine: first "showing" the sequence, then the player's turn.
 */
public class DetonatorModule implements BombModule {

    public static final String[] ARROWS = {"← LEFT", "→ RIGHT", "↑ UP", "↓ DOWN"};
    private static final int[] TONES = {550, 660, 880, 440};      // one tone per direction

    private static final int JX_SIGN = 1;       // flip to -1 if left and right are reversed
    private static final int JY_SIGN = -1;      // flip to 1 if up and down are reversed
    private static final int PUSH = 250;        // counts from the resting position: a real push
    private static final int CENTRE = 100;      // counts: back near the middle again
    private static final long STEP_MS = 1000;   // time given to each step of the show
    private static final long ON_MS = 700;      // part of each step where the arrow is lit
    private static final long LEAD_MS = 1200;   // pause before the show starts

    private final int[] sequence;               // directions 0 = left, 1 = right, 2 = up, 3 = down
    private boolean showing = true;             // true while the bomb plays the sequence
    private boolean waitingToShow = true;       // the show has not started its timer yet
    private long showStart = 0;
    private int lastShown = -1;                 // last step whose tone was already played
    private int pos = 0;                        // how many steps the player got right
    private boolean armed = false;              // true once the stick was back in the middle
    private boolean complete = false;
    private String status = "Get ready...";
    private String live = "Get ready...";       // text shown in the instruction box

    public DetonatorModule(int[] sequence) {
        this.sequence = sequence;
    }

    public String getName() { return "Detonator"; }

    // changes while the sequence is played, so the arrow appears on the big screen text
    public String getInstruction() { return "DETONATOR   " + live; }

    public ModuleResult processInput(SensorEvent e, Calibration cal) {
        if (complete || e.type != SensorEvent.SAMPLE) return ModuleResult.NONE;

        // ---- phase 1: the bomb shows the sequence, one arrow and tone per step ----
        if (showing) {
            if (waitingToShow) {
                showStart = e.time + LEAD_MS;
                waitingToShow = false;
                lastShown = -1;
            }
            long dt = e.time - showStart;               // time since the show began
            if (dt < 0) {
                live = "Watch and listen...";
            } else {
                int step = (int) (dt / STEP_MS);        // which arrow is on now
                if (step >= sequence.length) {
                    showing = false;                    // the player's turn
                    pos = 0;
                    armed = false;
                    live = "YOUR TURN: repeat it  (0/" + sequence.length + ")";
                } else {
                    boolean lit = dt % STEP_MS < ON_MS;
                    if (lit && step != lastShown) {
                        Sound.get().beep(TONES[sequence[step]], 400);
                        lastShown = step;
                    }
                    live = lit ? ARROWS[sequence[step]] : "...";
                }
            }
            status = live;
            return ModuleResult.NONE;
        }

        // ---- phase 2: the player's turn ----
        // joystick position relative to the calibrated centre
        int x = JX_SIGN * (e.jx - cal.jx0);
        int y = JY_SIGN * (e.jy - cal.jy0);
        if (Math.abs(x) < CENTRE && Math.abs(y) < CENTRE) {
            armed = true;                                // ready for the next push
            return ModuleResult.NONE;
        }
        if (!armed) return ModuleResult.NONE;
        // decide which way the stick was pushed; the bigger axis wins
        int dir;
        if (Math.abs(x) >= PUSH && Math.abs(x) >= Math.abs(y)) dir = x < 0 ? 0 : 1;
        else if (Math.abs(y) >= PUSH) dir = y > 0 ? 2 : 3;
        else return ModuleResult.NONE;                   // not pushed far enough yet
        armed = false;
        Sound.get().beep(TONES[dir], 150);

        // right push: next step, or done
        if (dir == sequence[pos]) {
            pos++;
            if (pos >= sequence.length) {
                complete = true;
                live = "Detonator disarmed";
                status = live;
                return ModuleResult.COMPLETE;
            }
            live = "YOUR TURN: " + pos + "/" + sequence.length;
            status = live;
            return ModuleResult.NONE;
        }
        // wrong direction: strike, then the sequence is shown again
        showing = true;
        waitingToShow = true;
        live = "Wrong! Watch again...";
        status = live;
        return ModuleResult.STRIKE;
    }

    public boolean isComplete() { return complete; }
    public int getProgress() { return complete ? 100 : showing ? 0 : pos * 100 / sequence.length; }
    public String getStatus() { return status; }

    public void reset() {
        showing = true;
        waitingToShow = true;
        lastShown = -1;
        pos = 0;
        armed = false;
        complete = false;
        status = "Get ready...";
        live = "Get ready...";
    }
}
