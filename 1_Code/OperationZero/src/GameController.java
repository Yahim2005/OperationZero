import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The brain of the game (central processing on the computer).
 *
 * Threads:
 *  - a reader thread blocks on the serial link and turns each line into a queued task;
 *  - one game-processing thread runs every task in order, so the game logic never
 *    needs locks;
 *  - the Swing thread only draws, from the Snapshots this class publishes (Observer).
 *
 * Flow of one attempt: READY -> WAITING_DEVICE (send "G", wait for data) ->
 * CALIBRATING (average 5 samples) -> RUNNING (mini-games one by one) ->
 * READY_TO_DEFUSE (press DEFUSE) -> DEFUSED, or FAILED when the time runs out.
 */
public class GameController {

    private final SerialLink link;                 // real ZigBee link or simulator
    private final String[] cardIds;                // RFID tag IDs: red, blue, green
    private final boolean useJoystick, useAlignment;
    private final Leaderboard leaderboard;
    private final Random random = new Random();
    private final StabilityMonitor stability = new StabilityMonitor();   // the constant "do not shake" rule

    // tasks for the single game-processing thread
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
    private final List<GameObserver> observers = new CopyOnWriteArrayList<GameObserver>();

    // only touched by the game-processing thread
    private GameState state = GameState.READY;
    private GameSession session;
    private String message = "Type your name and press START.";
    private long lastSampleMs, requestMs, lastPublish, frozenRemaining;   // timestamps used by tick()
    private int tries, calCount;                                           // connection tries; calibration samples
    private final long[] calSum = new long[4];                             // running sums for the calibration average

    public GameController(SerialLink link, String[] cardIds, boolean useJoystick,
                          boolean useAlignment, Leaderboard leaderboard) {
        this.link = link;
        this.cardIds = cardIds;
        this.useJoystick = useJoystick;
        this.useAlignment = useAlignment;
        this.leaderboard = leaderboard;
        Sound.get().attach(link);               // the buzzer sends its notes through the same link
    }

    // Observer pattern: register a listener (the screen)
    public void addObserver(GameObserver o) { observers.add(o); }

    // asks the game thread to publish the current state again
    public void refresh() { queue.offer(new Runnable() { public void run() { publish(); } }); }

    // called by the START button on the Swing thread; the work is queued for the game thread
    public void requestStart(final String name) {
        queue.offer(new Runnable() { public void run() { begin(name); } });
    }

    // Starts the reader thread and the game-processing thread
    public void start() {
        // reader thread: only reads lines and queues them, so slow serial reads never freeze the game
        Thread reader = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    final String line = link.readLine();
                    if (line == null || line.isEmpty()) {
                        try { Thread.sleep(10); } catch (InterruptedException e) { return; }
                        continue;
                    }
                    queue.offer(new Runnable() { public void run() { handleLine(line); } });
                }
            }
        });
        reader.setDaemon(true);
        reader.start();

        // game thread: runs queued tasks, and calls tick() at least every 100 ms for the timer
        Thread worker = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Runnable task = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (task != null) task.run();
                        tick();
                    } catch (InterruptedException e) {
                        return;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
    }

    // current time in milliseconds
    private long now() { return System.currentTimeMillis(); }

    // ---------- starting an attempt ----------

    // creates a new random bomb and asks the board to start streaming
    private void begin(String name) {
        // ignore START while an attempt is already running
        if (state == GameState.WAITING_DEVICE || state == GameState.CALIBRATING
                || state == GameState.RUNNING || state == GameState.READY_TO_DEFUSE) return;
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            message = "Please type your name first.";
            publish();
            return;
        }
        // new random bomb for this player
        BombConfiguration cfg = new BombConfiguration(random);
        session = new GameSession(new Player(n), cfg, cfg.createModules(cardIds, useJoystick, useAlignment));
        state = GameState.WAITING_DEVICE;
        message = "Connecting to the bomb...";
        tries = 1;
        requestMs = now();
        link.send("G");               // "G" = go: the board starts sending sensor lines
        publish();
    }

    // ---------- messages from the bomb ----------

    // called for every line that arrives from the bomb
    private void handleLine(String line) {
        SensorEvent e = SensorEvent.parse(line);
        if (e.type == SensorEvent.SAMPLE) lastSampleMs = e.time;

        // the board sent RDY in the middle of a game: it rebooted, so the attempt is void
        if (e.type == SensorEvent.READY && (state == GameState.CALIBRATING
                || state == GameState.RUNNING || state == GameState.READY_TO_DEFUSE)) {
            finish(GameState.INTERRUPTED, "The bomb restarted - attempt interrupted.");
            return;
        }

        // first data from the bomb: start calibrating
        if (state == GameState.WAITING_DEVICE
                && (e.type == SensorEvent.ACK || e.type == SensorEvent.SAMPLE)) {
            state = GameState.CALIBRATING;
            calCount = 0;
            Arrays.fill(calSum, 0);
            lastSampleMs = e.time;
            message = "Keep the bomb flat and the joystick centred...";
            publish();
        }

        // calibration: average the first 5 samples to learn the joystick centre and accelerometer rest values
        if (state == GameState.CALIBRATING) {
            if (e.type == SensorEvent.SAMPLE) {
                calSum[0] += e.jx; calSum[1] += e.jy; calSum[2] += e.ax; calSum[3] += e.ay;
                calCount++;
                if (calCount >= 5) {
                    session.cal.jx0 = (int) (calSum[0] / calCount);
                    session.cal.jy0 = (int) (calSum[1] / calCount);
                    session.cal.ax0 = (int) (calSum[2] / calCount);
                    session.cal.ay0 = (int) (calSum[3] / calCount);
                    beginRound();
                }
            }
            return;
        }

        // the whole game: shaking the bomb is a strike
        if ((state == GameState.RUNNING || state == GameState.READY_TO_DEFUSE)
                && e.type == SensorEvent.SAMPLE && stability.update(e, session.cal)) {
            session.strikes++;
            message = "STRIKE! You shook the bomb (+5 seconds)";
            Sound.get().strike();
            publish();
        }

        // hand the event to the active mini-game and react to its answer
        if (state == GameState.RUNNING) {
            BombModule m = session.active();
            ModuleResult r = m.processInput(e, session.cal);
            if (r == ModuleResult.STRIKE) {
                session.strikes++;
                message = "STRIKE! (+5 seconds)";
                Sound.get().strike();
                publish();
            } else if (r == ModuleResult.COMPLETE) {
                message = m.getName() + " complete!";
                Sound.get().success();
                session.activeIndex++;           // move on to the next mini-game
                if (session.allComplete()) {
                    state = GameState.READY_TO_DEFUSE;
                    message = "All modules done - press the DEFUSE button!";
                }
                publish();
            }
        } else if (state == GameState.READY_TO_DEFUSE) {
            // a fresh press after the button became available
            if (e.type == SensorEvent.BUTTON) {
                finish(GameState.DEFUSED, "");
            }
        }
    }

    // calibration is done: start the timer and the game
    private void beginRound() {
        link.send("Z");                 // zero the encoder
        stability.reset();
        session.startMs = now();
        state = GameState.RUNNING;
        message = "Defuse the bomb!";
        publish();
    }

    // ---------- timing ----------

    // called regularly by the game thread: handles timeouts and the countdown
    private void tick() {
        if (session == null) return;
        long t = now();
        switch (state) {
            case WAITING_DEVICE:
                // no answer after 2 s: ask again, give up after 3 tries
                if (t - requestMs > 2000) {
                    if (tries < 3) {
                        tries++;
                        requestMs = t;
                        link.send("G");
                    } else {
                        abort("The bomb is not responding. Check the connection.");
                    }
                }
                break;
            case CALIBRATING:
                // the data stopped: the link is down
                if (t - lastSampleMs > 2500) abort("The bomb stopped sending data.");
                break;
            case RUNNING:
            case READY_TO_DEFUSE:
                // time left on the clock; sensor silence for 2.5 s means the link is lost
                long remaining = session.config.timeLimitMs - (t - session.startMs);
                if (remaining <= 0) {
                    finish(GameState.FAILED, "Time is up - the bomb exploded!");
                } else if (t - lastSampleMs > 2500) {
                    finish(GameState.INTERRUPTED, "Connection lost - attempt interrupted.");
                } else if (t - lastPublish >= 100) {
                    publish();
                }
                break;
            default:
                break;
        }
    }

    // stops an attempt that never really started (no result is saved)
    private void abort(String msg) {
        link.send("X");
        frozenRemaining = session.config.timeLimitMs;
        state = GameState.INTERRUPTED;
        message = msg;
        publish();
    }

    // ---------- ending an attempt ----------

    // ends the attempt, works out the score, saves it on the leaderboard and plays the final sound
    private void finish(GameState end, String msg) {
        link.send("X");               // "X" = stop streaming
        long elapsed = Math.min(now() - session.startMs, session.config.timeLimitMs);
        long adjusted = elapsed + session.strikes * 5000L;     // each strike adds 5 seconds
        // score: 1000 minus one point per 0.2 s of adjusted time (0 if not defused)
        int score = end == GameState.DEFUSED ? Math.max(0, 1000 - (int) (adjusted / 200)) : 0;
        GameResult.Outcome o = end == GameState.DEFUSED ? GameResult.Outcome.DEFUSED
                : end == GameState.FAILED ? GameResult.Outcome.FAILED : GameResult.Outcome.INTERRUPTED;
        leaderboard.add(new GameResult(session.player.name, o, elapsed, session.strikes,
                adjusted, score, session.config.id));
        leaderboard.save();
        frozenRemaining = Math.max(0, session.config.timeLimitMs - elapsed);
        state = end;
        if (end == GameState.DEFUSED) Sound.get().victory();
        else if (end == GameState.FAILED) Sound.get().explosion();
        if (end == GameState.DEFUSED)
            message = String.format("BOMB DEFUSED!  %.1fs + %d strikes = %.1fs   Score: %d",
                    elapsed / 1000.0, session.strikes, adjusted / 1000.0, score);
        else
            message = msg;
        publish();
    }

    // ---------- telling the observers ----------

    // builds a Snapshot of the whole game and sends it to every observer
    private void publish() {
        lastPublish = now();
        Snapshot s = new Snapshot();
        s.state = state;
        s.message = message;
        s.leaderboardText = leaderboard.toText();
        if (session != null) {
            s.player = session.player.name;
            s.strikes = session.strikes;
            s.stability = (state == GameState.RUNNING || state == GameState.READY_TO_DEFUSE)
                    ? stability.getLevel() : 0;
            s.active = session.activeIndex;
            // one row per mini-game for the list on the screen
            int n = session.modules.size();
            s.moduleNames = new String[n];
            s.progress = new int[n];
            s.status = new String[n];
            for (int i = 0; i < n; i++) {
                BombModule m = session.modules.get(i);
                s.moduleNames[i] = m.getName();
                s.progress[i] = i < session.activeIndex ? 100 : m.getProgress();
                s.status[i] = i < session.activeIndex ? "Done" : (i == session.activeIndex ? m.getStatus() : "");
            }
            // text and maze picture of the mini-game being played now
            BombModule a = session.active();
            if (state == GameState.RUNNING && a != null) {
                s.instruction = a.getInstruction();
                s.scene = a.getScene();
            }
            else if (state == GameState.READY_TO_DEFUSE) s.instruction = "Press the DEFUSE button now!";
            // clock: running = live, finished = frozen at the final value, idle = full time
            if (state == GameState.RUNNING || state == GameState.READY_TO_DEFUSE)
                s.remainingMs = Math.max(0, session.config.timeLimitMs - (now() - session.startMs));
            else if (state == GameState.DEFUSED || state == GameState.FAILED || state == GameState.INTERRUPTED)
                s.remainingMs = frozenRemaining;
            else s.remainingMs = session.config.timeLimitMs;
        } else {
            s.remainingMs = 180000;
        }
        for (GameObserver o : observers) o.update(s);
    }
}
