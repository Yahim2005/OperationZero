/**
 * Mini-game 6, "Override Code" (keypad).
 * The screen shows a clue (for example a famous birthday) and the player works out
 * the code, types it on the keypad and confirms with #. * clears what was typed.
 * A wrong code is a strike.
 */
public class KeypadModule implements BombModule {

    private final String code;      // the right answer, never shown on screen
    private final String clue;
    private final StringBuilder entered = new StringBuilder();   // digits typed so far
    private boolean complete = false;
    private String status = "Type the code, then press #";

    public KeypadModule(String code, String clue) {
        this.code = code;
        this.clue = clue;
    }

    public String getName() { return "Override Code"; }

    public String getInstruction() {
        // the code itself is never shown, only the clue
        return clue + " Type it on the keypad, then press # (* clears).";
    }

    public ModuleResult processInput(SensorEvent e, Calibration cal) {
        if (complete || e.type != SensorEvent.KEY) return ModuleResult.NONE;
        char k = e.text.charAt(0);
        if (k >= '0' && k <= '9') {
            // digit: add it, but never more digits than the code has
            if (entered.length() < code.length()) entered.append(k);
            status = "Entered: " + entered.toString().replaceAll(".", "*");   // hide the digits
        } else if (k == '*') {
            entered.setLength(0);
            status = "Cleared";
        } else if (k == '#') {
            // # submits the attempt
            boolean ok = entered.toString().equals(code);
            entered.setLength(0);
            if (ok) {
                complete = true;
                status = "Code accepted";
                return ModuleResult.COMPLETE;
            }
            status = "Wrong code!";
            return ModuleResult.STRIKE;
        }
        return ModuleResult.NONE;
    }

    public boolean isComplete() { return complete; }
    // progress only reaches 50% while typing; the other half is the # confirmation
    public int getProgress() { return complete ? 100 : entered.length() * 100 / code.length() / 2; }
    public String getStatus() { return status; }

    public void reset() {
        complete = false;
        entered.setLength(0);
        status = "Type the code, then press #";
    }
}
