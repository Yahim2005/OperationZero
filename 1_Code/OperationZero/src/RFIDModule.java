/**
 * Mini-game 1, "Security Authorization" (RFID reader).
 * The screen asks a question whose answer is a colour (red, blue or green).
 * The player scans the card of that colour; a wrong card is a strike.
 */
public class RFIDModule implements BombModule {

    private final String[] cardIds;     // tag IDs: 0 = red, 1 = blue, 2 = green
    private final String[] cardNames;
    private final int required;         // index of the correct card
    private final String question;
    private boolean complete = false;
    private String status = "Waiting for a card";
    private String lastTag = "";        // used to ignore repeated reads of one scan
    private long lastTagTime = 0;

    public RFIDModule(String[] cardIds, String[] cardNames, int required, String question) {
        this.cardIds = cardIds;
        this.cardNames = cardNames;
        this.required = required;
        this.question = question;
    }

    public String getName() { return "Security Authorization"; }

    public String getInstruction() {
        // the answer is a colour: the player must know it and scan the card of that colour
        return "SECURITY QUESTION: " + question + " Scan the card of that colour.";
    }

    public ModuleResult processInput(SensorEvent e, Calibration cal) {
        if (complete || e.type != SensorEvent.TAG) return ModuleResult.NONE;
        // the same scan repeated while the card is held counts as one action
        if (e.text.equals(lastTag) && e.time - lastTagTime < 1500) {
            lastTagTime = e.time;
            return ModuleResult.NONE;
        }
        lastTag = e.text;
        lastTagTime = e.time;
        if (e.text.equals(cardIds[required])) {
            complete = true;
            status = "Access granted";
            return ModuleResult.COMPLETE;
        }
        status = "Wrong card! Intruder alert!";
        return ModuleResult.STRIKE;
    }

    public boolean isComplete() { return complete; }
    public int getProgress() { return complete ? 100 : 0; }
    public String getStatus() { return status; }

    public void reset() {
        complete = false;
        status = "Waiting for a card";
        lastTag = "";
        lastTagTime = 0;
    }
}
