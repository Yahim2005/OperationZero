/**
 * Strategy pattern: one mini-game on the bomb. GameController only knows this
 * interface, so a new mini-game can be added without changing the controller.
 */
public interface BombModule {
    String getName();
    String getInstruction();
    /** Feed one sensor event; the module answers NONE, STRIKE or COMPLETE. */
    ModuleResult processInput(SensorEvent e, Calibration cal);
    boolean isComplete();
    int getProgress();          // 0 - 100
    String getStatus();         // live feedback text for the screen
    void reset();

    // optional picture for the screen (rows of characters); null for modules without one
    default String[] getScene() { return null; }
}
