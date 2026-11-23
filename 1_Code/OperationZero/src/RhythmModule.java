/**
 * Mini-game 3, "Rhythm Lock" (buzzer + DEFUSE button).
 * The bomb plays a rhythm of four beeps on the buzzer. The player taps the same
 * rhythm on the DEFUSE button (short or long gaps between the taps).
 * A wrong gap is a strike and the rhythm is played again.
 * Phases: START (play it) -> PLAYING (listen) -> INPUT (player taps).
 */
public class RhythmModule implements BombModule {

    private static final int HZ = 1000;                 // pitch of the beeps
    private static final int BEEP_MS = 90;
    private static final int SHORT_GAP = 300;           // ms between two beeps
    private static final int LONG_GAP = 700;
    private static final int SPLIT = 500;               // a tap gap above this counts as long
    private static final long LEAD_IN = 500;            // silence before the first beep
    private static final long TOO_SLOW = 3000;          // ms without a tap: listen again

    private final boolean[] longGap;                    // 3 gaps between 4 beeps: true = long
    private static final int START = 0, PLAYING = 1, INPUT = 2;
    private int phase = START;
    private long playStart, playEnd, lastTap;           // timestamps of the playback and of the last tap
    private int taps = 0;                               // taps made so far
    private boolean complete = false;
    private String status = "LISTEN...";
    private String live = "LISTEN...";

    public RhythmModule(boolean[] longGap) {
        this.longGap = longGap;
    }

    public String getName() { return "Rhythm Lock"; }

    public String getInstruction() { return "RHYTHM   " + live; }

    public ModuleResult processInput(SensorEvent e, Calibration cal) {
        if (complete) return ModuleResult.NONE;
        long t = e.time;

        // sensor samples arrive every 100 ms, so they act as the clock for this module
        if (e.type == SensorEvent.SAMPLE) {
            if (phase == START) startPlayback(t);
            if (phase == PLAYING) {
                live = "LISTEN: " + beatsHeard(t);
                status = "LISTEN TO THE RHYTHM...";
                if (t >= playEnd) {
                    phase = INPUT;
                    taps = 0;
                    live = "YOUR TURN: TAP IT ON THE DEFUSE BUTTON";
                    status = "TAP 0/4";
                }
            } else if (phase == INPUT && taps > 0 && t - lastTap > TOO_SLOW) {
                live = "TOO SLOW - LISTEN AGAIN";
                status = live;
                phase = START;
            }
            return ModuleResult.NONE;
        }

        // a DEFUSE button press is one tap; taps during playback are ignored
        if (e.type == SensorEvent.BUTTON && phase == INPUT) {
            if (taps == 0) {
                taps = 1;
                lastTap = t;
                status = "TAP 1/4";
                return ModuleResult.NONE;
            }
            // compare the gap since the previous tap with the expected short/long gap
            boolean isLong = t - lastTap >= SPLIT;
            lastTap = t;
            if (isLong != longGap[taps - 1]) {
                live = "WRONG RHYTHM! LISTEN AGAIN";
                status = live;
                phase = START;
                taps = 0;
                return ModuleResult.STRIKE;
            }
            taps++;
            status = "TAP " + taps + "/4";
            if (taps >= 4) {
                complete = true;
                live = "RHYTHM LOCKED";
                status = live;
                return ModuleResult.COMPLETE;
            }
        }
        return ModuleResult.NONE;
    }

    // plays the beeps (a frequency of 0 is a silence) and works out when they are over
    private void startPlayback(long t) {
        int n = 4 + 3 + 1;
        int[] hz = new int[n], ms = new int[n];
        int i = 0;
        hz[i] = 0; ms[i++] = (int) LEAD_IN - 25;
        long total = LEAD_IN;
        for (int b = 0; b < 4; b++) {
            hz[i] = HZ; ms[i++] = BEEP_MS;
            total += BEEP_MS + 25;
            if (b < 3) {
                int gap = longGap[b] ? LONG_GAP : SHORT_GAP;
                int rest = gap - (BEEP_MS + 25) - 25;           // each note also waits 25 ms
                hz[i] = 0; ms[i++] = rest;
                total += rest + 25;
            }
        }
        Sound.get().melody(hz, ms);
        playStart = t;
        playEnd = t + total + 400;
        phase = PLAYING;
        taps = 0;
    }

    // shows a star for every beep that has played so far
    private String beatsHeard(long t) {
        long at = playStart + LEAD_IN;
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < 4; b++) {
            if (t >= at) sb.append("* ");
            at += longGap.length > b ? (longGap[b] ? LONG_GAP : SHORT_GAP) : 0;
        }
        return sb.toString().trim();
    }

    public boolean isComplete() { return complete; }
    public int getProgress() { return complete ? 100 : phase == INPUT ? taps * 100 / 4 : 0; }
    public String getStatus() { return status; }

    public void reset() {
        phase = START;
        taps = 0;
        complete = false;
        status = "LISTEN...";
        live = "LISTEN...";
    }
}
