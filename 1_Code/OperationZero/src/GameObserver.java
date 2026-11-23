/**
 * Observer pattern: anything that wants to follow the game (the screen, for example)
 * implements this interface and registers with GameController.
 * The controller calls update() every time the game changes.
 */
public interface GameObserver {
    /** Called with a fresh read-only picture of the game. */
    void update(Snapshot s);
}
