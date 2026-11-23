/**
 * Early mini-game (not used in the final 6-game flow): turn a rotary encoder to a
 * target level and hold it. Dropped because the encoder was not fitted on the final
 * bomb. Another HoldModule example.
 */
public class PowerDialModule extends HoldModule {

    private final int target;

    public PowerDialModule(int target, long holdMs) {
        super(holdMs);
        this.target = target;
    }

    public String getName() { return "Power Dial"; }

    public String getInstruction() {
        return "Turn the dial to level " + target + " and hold it there.";
    }

    protected boolean inRange(SensorEvent e, Calibration cal) {
        int level = Math.abs(e.enc);           // direction does not matter
        int diff = Math.abs(level - target);
        // colour hint: green = right level, yellow = close, red = far
        if (diff <= 1) status = "Level " + level + " - CORRECT (green)";
        else if (diff <= 4) status = "Level " + level + " - close (yellow)";
        else status = "Level " + level + " - far (red)";
        return diff <= 1;
    }
}
