/**
 * The final result of one attempt. It is what gets saved on the leaderboard.
 * adjustedMs is the time the player is ranked by: elapsed time plus the strike penalty.
 */
public class GameResult {

    public enum Outcome { DEFUSED, FAILED, INTERRUPTED }

    public final String name;
    public final Outcome outcome;
    public final long elapsedMs;     // real time played
    public final int strikes;
    public final long adjustedMs;    // elapsed + penalty per strike
    public final int score;
    public final long configId;      // which random bomb layout was played

    public GameResult(String name, Outcome outcome, long elapsedMs, int strikes,
                      long adjustedMs, int score, long configId) {
        this.name = name;
        this.outcome = outcome;
        this.elapsedMs = elapsedMs;
        this.strikes = strikes;
        this.adjustedMs = adjustedMs;
        this.score = score;
        this.configId = configId;
    }

    /** Text shown on the end screen, e.g. "Sam  104.2s + 2 strikes = 114.2s  (score 430)". */
    public String toString() {
        return String.format("%s  %.1fs + %d strikes = %.1fs  (score %d)",
                name, elapsedMs / 1000.0, strikes, adjustedMs / 1000.0, score);
    }
}
