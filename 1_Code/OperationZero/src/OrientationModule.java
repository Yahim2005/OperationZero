/**
 * Early mini-game (not used in the final 6-game flow): tilt the board in a given
 * direction and hold it. Kept as an example of the Template Method pattern
 * (it only supplies inRange(); the timing logic lives in HoldModule).
 */
public class OrientationModule extends HoldModule {

    public static final String[] DIRS = {"LEFT", "RIGHT", "FORWARD", "BACK"};

    // Flip these to -1 if left/right or forward/back feel reversed on your board
    private static final int X_SIGN = 1;
    private static final int Y_SIGN = 1;
    private static final int TILT = 25;     // counts away from flat to START the hold
    private static final int FLAT = 45;     // the other axis must stay near flat
    private static final double KEEP = 0.7; // once holding, only 70% of TILT is needed
    private static final double SMOOTH = 0.3;   // 0.1 = very smooth, 1 = no smoothing

    private double fa = 0, fb = 0;          // smoothed tilt on the two axes
    private boolean started = false;

    private final int dir;

    public OrientationModule(int dir, long holdMs) {
        super(holdMs);
        this.dir = dir;
    }

    public String getName() { return "Core Stabilization"; }

    public String getInstruction() {
        return "Tilt the core " + DIRS[dir] + " and hold it steady.";
    }

    protected boolean inRange(SensorEvent e, Calibration cal) {
        // tilt = reading minus the resting value measured at calibration
        double a = X_SIGN * (e.ax - cal.ax0);
        double b = Y_SIGN * (e.ay - cal.ay0);
        // low-pass filter so sensor noise does not break the hold
        if (!started) { fa = a; fb = b; started = true; }
        else { fa += SMOOTH * (a - fa); fb += SMOOTH * (b - fb); }
        status = "Tilt x=" + (int) fa + " y=" + (int) fb;
        double t = holdStart == 0 ? TILT : TILT * KEEP;     // easier to keep than to start
        switch (dir) {
            case 0: return fa <= -t && Math.abs(fb) <= FLAT;
            case 1: return fa >= t && Math.abs(fb) <= FLAT;
            case 2: return fb >= t && Math.abs(fa) <= FLAT;
            default: return fb <= -t && Math.abs(fa) <= FLAT;
        }
    }

    public void reset() {
        super.reset();
        started = false;
    }
}
