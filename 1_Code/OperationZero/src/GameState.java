/**
 * The states of one game attempt, as tracked by GameController.
 * READY -> WAITING_DEVICE -> CALIBRATING -> RUNNING -> READY_TO_DEFUSE -> DEFUSED / FAILED.
 * INTERRUPTED is used when the player stops the attempt or the link drops.
 */
public enum GameState {
    READY, WAITING_DEVICE, CALIBRATING, RUNNING, READY_TO_DEFUSE, DEFUSED, FAILED, INTERRUPTED
}
