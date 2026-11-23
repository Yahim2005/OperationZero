import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Everything that is random in one attempt: the security question, the detonator
 * sequence, the radar target, the rhythm, the maze, and the keypad clue and code.
 * Built once per game from a Random, so every player gets a different bomb.
 */
public class BombConfiguration {

    public static final String[] CARD_NAMES = {"RED", "BLUE", "GREEN"};

    public final long id;                         // identifies this layout on the leaderboard
    public final long timeLimitMs = 180000;       // three minutes
    public final int requiredCard;                // the answer to the security question
    public final String question;
    public final int[] detonatorSequence;         // 4 joystick directions to remember
    public final int alignTarget;                 // hidden distance for the radar
    public final boolean[] rhythm;                // 3 gaps between 4 beeps: true = long
    public final long mazeSeed;                   // layout of the pellets in the Pac-Man game
    public final String codeClue;                 // shown on screen
    public final String code;                     // typed by the player, never shown

    public BombConfiguration(Random r) {
        id = System.currentTimeMillis();
        // the question and its answer colour go together
        requiredCard = r.nextInt(3);
        question = Clues.question(r, requiredCard);
        // detonator: 4 random directions (0 left, 1 right, 2 up, 3 down)
        detonatorSequence = new int[4];
        for (int i = 0; i < detonatorSequence.length; i++) detonatorSequence[i] = r.nextInt(4);
        alignTarget = 10 + r.nextInt(21);         // 10 - 30 cm
        // rhythm: re-roll until the gaps are not all the same
        rhythm = new boolean[3];
        do {
            for (int i = 0; i < rhythm.length; i++) rhythm[i] = r.nextBoolean();
        } while (rhythm[0] == rhythm[1] && rhythm[1] == rhythm[2]);     // at least one short and one long gap
        mazeSeed = r.nextLong();
        // keypad clue and its matching code
        String[] cc = Clues.codeClue(r);
        codeClue = cc[0];
        code = cc[1];
    }

    // The six mini-games: security question (RFID), detonator (joystick + buzzer),
    // rhythm lock (button + buzzer), ghost hunt (joystick + buzzer + screen),
    // radar (ultrasonic + buzzer), override code (keypad).
    // The accelerometer is not a mini-game: shaking the bomb is a strike during the whole game.
    public List<BombModule> createModules(String[] cardIds, boolean useJoystick, boolean useAlignment) {
        // order in the list = order the player plays them
        List<BombModule> list = new ArrayList<BombModule>();
        list.add(new RFIDModule(cardIds, CARD_NAMES, requiredCard, question));
        if (useJoystick) list.add(new DetonatorModule(detonatorSequence));
        list.add(new RhythmModule(rhythm));
        if (useJoystick) list.add(new MazeModule(mazeSeed));
        if (useAlignment) list.add(new AlignmentModule(alignTarget, 2000));
        list.add(new KeypadModule(code, codeClue));
        return list;
    }
}
