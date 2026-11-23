import java.util.List;

/**
 * Everything about the attempt that is running now: who plays, the random bomb
 * layout, the list of mini-games, the calibration, the strikes so far, and which
 * mini-game is active. Mini-games are played one after the other, in list order.
 */
public class GameSession {
    public final Player player;
    public final BombConfiguration config;
    public final List<BombModule> modules;
    public final Calibration cal = new Calibration();
    public int strikes = 0;
    public int activeIndex = 0;   // index of the mini-game being played
    public long startMs = 0;      // time the timer started

    public GameSession(Player player, BombConfiguration config, List<BombModule> modules) {
        this.player = player;
        this.config = config;
        this.modules = modules;
    }

    /** The mini-game being played, or null when all are finished. */
    public BombModule active() {
        return activeIndex < modules.size() ? modules.get(activeIndex) : null;
    }

    public boolean allComplete() {
        return activeIndex >= modules.size();
    }
}
