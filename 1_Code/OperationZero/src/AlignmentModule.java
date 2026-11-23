/**
 * Mini-game 5, "Radar Lock" (ultrasonic sensor + buzzer). The target distance is hidden.
 * The buzzer beeps faster and higher the closer the block is to the secret spot, and
 * plays a steady tone when it is locked. Extends HoldModule: the player must stay
 * locked for the hold time.
 */
public class AlignmentModule extends HoldModule {

    private static final int TOLERANCE = 2;     // cm around the target that counts as locked

    private final int target;       // secret distance in cm
    private long lastBeep = 0;      // time of the last proximity beep

    public AlignmentModule(int target, long holdMs) {
        super(holdMs);
        this.target = target;
    }

    public String getName() { return "Radar Lock"; }

    public String getInstruction() {
        return "A hidden spot lies in front of the radar. Slide the block back and forth: "
                + "the closer you get, the faster the bomb beeps. Find it and hold still.";
    }

    protected boolean inRange(SensorEvent e, Calibration cal) {
        if (e.cm <= 0) {                        // 0 means the sensor sees nothing
            status = "No signal - put the block in front of the radar";
            return false;
        }
        int d = Math.abs(e.cm - target);   // distance from the secret spot
        // hot/cold words instead of numbers, so the player cannot just read the answer
        status = d <= TOLERANCE ? "LOCKED!" : d <= 5 ? "Burning hot" : d <= 10 ? "Warm"
                : d <= 20 ? "Cool" : "Freezing";

        // closer = higher pitch (500 - 2000 Hz) and shorter gap between the beeps
        int pitch = 500 + Math.max(0, 30 - d) * 50;
        long gap = d <= TOLERANCE ? 0 : Math.min(700, 100 + d * 30);
        if (e.time - lastBeep >= gap) {   // send a beep only when the gap has passed
            Sound.get().beep(pitch, 80);
            lastBeep = e.time;
        }
        return d <= TOLERANCE;
    }

    public void reset() {
        super.reset();
        lastBeep = 0;
    }
}
