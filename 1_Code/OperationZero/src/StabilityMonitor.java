/**
 * Watches the accelerometer during the whole game: shaking the bomb is a strike.
 * GameController calls update() with every sensor sample while the game runs.
 */
// Two things count as "unstable": the bomb tilted a long way from its resting position,
// or the readings jumping around a lot (shaking). One strike at most every few seconds.
public class StabilityMonitor {

    private static final double TILT_LIMIT = 55;     // counts from the resting value (about 55 degrees)
    private static final double SHAKE_LIMIT = 40;    // average change per sample over the last few samples
    private static final double WARN_LEVEL = 60;     // percent: the buzzer starts to warn
    private static final long COOLDOWN = 3000;       // ms between two shake strikes
    private static final int WINDOW = 5;             // samples used for the shake average
    private static final double SMOOTH = 0.4;

    private final int[] jumps = new int[WINDOW];   // size of the last few sample-to-sample changes
    private int jumpPos = 0;
    private double fx, fy;                          // smoothed tilt on each axis
    private int lastX, lastY;                       // previous raw readings
    private boolean started = false;
    private long quietUntil = 0, lastWarn = 0;
    private int level = 0;                           // 0 = steady, 100 = shaken
    private int overCount = 0;                       // samples in a row at 100 percent

    // forget everything (called at the start of each attempt)
    public void reset() {
        java.util.Arrays.fill(jumps, 0);
        jumpPos = 0;
        started = false;
        quietUntil = 0;
        lastWarn = 0;
        level = 0;
        overCount = 0;
    }

    // 0..100, drawn as the STABILITY bar on the screen
    public int getLevel() { return level; }

    // returns true when the bomb has just been shaken (the game adds a strike)
    public boolean update(SensorEvent e, Calibration cal) {
        // tilt from the resting position measured at calibration
        int x = e.ax - cal.ax0;
        int y = e.ay - cal.ay0;
        if (!started) {
            fx = x; fy = y;
            lastX = e.ax; lastY = e.ay;
            started = true;
        }
        // low-pass filter for the tilt, plus a moving average of the jumps for shaking
        fx += SMOOTH * (x - fx);
        fy += SMOOTH * (y - fy);
        jumps[jumpPos] = Math.max(Math.abs(e.ax - lastX), Math.abs(e.ay - lastY));
        jumpPos = (jumpPos + 1) % WINDOW;
        lastX = e.ax;
        lastY = e.ay;

        // the level is whichever is worse: tilt or shake, scaled so 100 = over the limit
        double sum = 0;
        for (int j : jumps) sum += j;
        double tilt = Math.max(Math.abs(fx), Math.abs(fy)) * 100 / TILT_LIMIT;
        double shake = sum / WINDOW * 100 / SHAKE_LIMIT;
        level = (int) Math.min(100, Math.max(tilt, shake));

        overCount = level >= 100 ? overCount + 1 : 0;
        if (overCount >= 2 && e.time >= quietUntil) {          // two samples in a row: not just a bump
            quietUntil = e.time + COOLDOWN;
            overCount = 0;
            return true;
        }
        if (level >= WARN_LEVEL && e.time - lastWarn > 450 && e.time >= quietUntil) {
            lastWarn = e.time;
            Sound.get().beep(180, 60);                // low warning tick
        }
        return false;
    }
}
