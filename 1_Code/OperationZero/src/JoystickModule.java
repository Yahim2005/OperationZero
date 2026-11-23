/**
 * Early mini-game (not used in the final 6-game flow): push the joystick in a given
 * direction and hold it. Replaced by DetonatorModule. Another HoldModule example.
 */
public class JoystickModule extends HoldModule {

    public static final String[] DIRS = {"LEFT", "RIGHT", "UP", "DOWN"};

    private static final int JX_SIGN = 1;
    private static final int JY_SIGN = 1;
    private static final int PUSH = 120;    // counts away from the resting position
    private static final int SIDE = 120;    // the other axis must stay near centre

    private final int dir;

    public JoystickModule(int dir, long holdMs) {
        super(holdMs);
        this.dir = dir;
    }

    public String getName() { return "Detonator Calibration"; }

    public String getInstruction() {
        return "Push the joystick " + DIRS[dir] + " and hold it there.";
    }

    protected boolean inRange(SensorEvent e, Calibration cal) {
        // stick position relative to its calibrated centre
        int a = JX_SIGN * (e.jx - cal.jx0);
        int b = JY_SIGN * (e.jy - cal.jy0);
        status = "Stick x=" + a + " y=" + b;
        switch (dir) {
            case 0: return a <= -PUSH && Math.abs(b) <= SIDE;
            case 1: return a >= PUSH && Math.abs(b) <= SIDE;
            case 2: return b >= PUSH && Math.abs(a) <= SIDE;
            default: return b <= -PUSH && Math.abs(a) <= SIDE;
        }
    }
}
