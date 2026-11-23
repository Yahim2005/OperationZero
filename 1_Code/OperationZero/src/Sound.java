import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Singleton pattern: the one passive buzzer on the bomb, shared by the whole game.
 * Every note is sent to the LilyPad as "N,<frequency Hz>,<duration ms>".
 * A single background thread plays the notes in order, so melodies never block the game.
 */
public class Sound {

    // the one and only instance, created when the class loads
    private static final Sound INSTANCE = new Sound();

    // single worker thread (daemon, so it never keeps the program alive); notes queue up behind each other
    private final ExecutorService player = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "buzzer");
            t.setDaemon(true);
            return t;
        }
    });
    private volatile SerialLink link;   // where the notes are sent

    private Sound() { }   // private: nobody else can create a second buzzer

    public static Sound get() { return INSTANCE; }

    // called once by the GameController
    public void attach(SerialLink link) { this.link = link; }

    // one short beep, queued behind whatever is already playing
    public void beep(final int hz, final int ms) {
        player.execute(new Runnable() {
            public void run() { note(hz, ms); }
        });
    }

    // a series of notes (hz[i] for ms[i]); a frequency of 0 is a silence
    public void melody(final int[] hz, final int[] ms) {
        player.execute(new Runnable() {
            public void run() {
                for (int i = 0; i < hz.length; i++) {
                    note(hz[i], ms[i]);
                    pause(ms[i] + 25);
                }
            }
        });
    }

    // sends one note to the board (does nothing before the link is attached)
    private void note(int hz, int ms) {
        SerialLink l = link;
        if (l != null) l.send("N," + hz + "," + ms);
    }

    private void pause(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---------- the game's sound effects ----------

    // low double buzz when the player makes a mistake
    public void strike() {
        melody(new int[]{300, 180}, new int[]{140, 260});
    }

    // quick rising chirp when a mini-game is solved
    public void success() {
        melody(new int[]{880, 1320}, new int[]{90, 140});
    }

    // short fanfare when the bomb is defused
    public void victory() {
        melody(new int[]{523, 659, 784, 1047}, new int[]{150, 150, 150, 450});
    }

    // falling tones when time runs out
    public void explosion() {
        melody(new int[]{900, 700, 520, 400, 300, 220, 160, 110},
               new int[]{70, 70, 80, 90, 110, 130, 160, 300});
    }
}
