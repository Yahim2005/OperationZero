/**
 * Read-only picture of the game, sent to the observers (the screen).
 * GameController fills one in each time something changes, so the screen never
 * touches the game logic directly.
 */
public class Snapshot {
    public GameState state;
    public String message = "";               // banner message (strike, hint...)
    public String player = "";
    public long remainingMs;                  // time left on the clock
    public int strikes;
    public int stability;          // 0 = steady, 100 = the bomb is being shaken
    public int active = -1;                   // index of the active mini-game
    public String instruction = "";           // what the player must do now
    public String[] scene;         // maze picture of the active module (null = none)
    public String[] moduleNames = new String[0];
    public int[] progress = new int[0];       // 0..100 per module
    public String[] status = new String[0];   // live status text per module
    public String leaderboardText = "";
}
