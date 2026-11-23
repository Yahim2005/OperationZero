/**
 * Template Method pattern: shared logic for mini-games where the player must keep a
 * sensor value inside an accepted range for a while (for example Radar Lock).
 * processInput() is the fixed algorithm (timing, grace period, progress);
 * subclasses only supply the changing step, inRange().
 */
public abstract class HoldModule implements BombModule {

    protected final long holdMs;          // how long the value must be held
    protected long holdStart = 0;         // when the current hold began (0 = not holding)
    protected long outSince = 0;          // when the value last left the range (0 = still inside)
    protected long graceMs = 600;      // short drops out of range are tolerated
    protected boolean complete = false;
    protected int progress = 0;
    protected String status = "";

    public HoldModule(long holdMs) {
        this.holdMs = holdMs;
    }

    // true if the sensor is inside the accepted range (also sets status)
    protected abstract boolean inRange(SensorEvent e, Calibration cal);

    public ModuleResult processInput(SensorEvent e, Calibration cal) {
        // only sensor samples matter here
        if (complete || e.type != SensorEvent.SAMPLE) return ModuleResult.NONE;
        if (inRange(e, cal)) {
            outSince = 0;
            if (holdStart == 0) holdStart = e.time;          // start timing the hold
            long held = e.time - holdStart;
            progress = (int) Math.min(100, held * 100 / holdMs);
            if (held >= holdMs) {                             // held long enough: solved
                complete = true;
                progress = 100;
                return ModuleResult.COMPLETE;
            }
        } else if (holdStart != 0) {
            if (outSince == 0) outSince = e.time;
            if (e.time - outSince > graceMs) {   // out of range for too long: start again
                holdStart = 0;
                outSince = 0;
                progress = 0;
            }
        }
        return ModuleResult.NONE;
    }

    public boolean isComplete() { return complete; }
    public int getProgress() { return progress; }
    public String getStatus() { return status; }

    public void reset() {
        holdStart = 0;
        outSince = 0;
        complete = false;
        progress = 0;
        status = "";
    }
}
