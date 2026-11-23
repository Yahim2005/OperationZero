import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps every result and saves them to a file with Java Data I/O streams (Lab 7),
 * so the scores are still there the next time the game starts.
 * All methods are synchronized because the game thread adds results while the
 * screen thread reads the text.
 */
public class Leaderboard {

    private final String fileName;
    private final List<GameResult> results = new ArrayList<GameResult>();

    public Leaderboard(String fileName) {
        this.fileName = fileName;
        load();   // read the old scores, if the file exists
    }

    public synchronized void add(GameResult r) {
        results.add(r);
    }

    // Data I/O streams, as in Lab 7
    public synchronized void save() {
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(fileName)))) {
            out.writeInt(results.size());   // first the count, then every result field by field
            for (GameResult r : results) {
                out.writeUTF(r.name);
                out.writeUTF(r.outcome.name());
                out.writeLong(r.elapsedMs);
                out.writeInt(r.strikes);
                out.writeLong(r.adjustedMs);
                out.writeInt(r.score);
                out.writeLong(r.configId);
            }
        } catch (IOException e) {
            System.out.println("Could not save the leaderboard: " + e.getMessage());
        }
    }

    // reads the file back in the same order it was written
    private void load() {
        File f = new File(fileName);
        if (!f.exists()) return;
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(f)))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String name = in.readUTF();
                GameResult.Outcome o = GameResult.Outcome.valueOf(in.readUTF());
                long elapsed = in.readLong();
                int strikes = in.readInt();
                long adjusted = in.readLong();
                int score = in.readInt();
                long cfg = in.readLong();
                results.add(new GameResult(name, o, elapsed, strikes, adjusted, score, cfg));
            }
        } catch (IOException e) {
            System.out.println("Could not read the leaderboard: " + e.getMessage());
        }
    }

    // Successful defusals ranked by adjusted time; failed and interrupted listed apart
    public synchronized String toText() {
        List<GameResult> ok = new ArrayList<GameResult>();
        List<GameResult> other = new ArrayList<GameResult>();
        for (GameResult r : results) {
            if (r.outcome == GameResult.Outcome.DEFUSED) ok.add(r);
            else other.add(r);
        }
        // fastest adjusted time first
        Collections.sort(ok, new Comparator<GameResult>() {
            public int compare(GameResult a, GameResult b) {
                return Long.compare(a.adjustedMs, b.adjustedMs);
            }
        });
        StringBuilder sb = new StringBuilder("LEADERBOARD (defused)\n");
        for (int i = 0; i < ok.size() && i < 10; i++)   // top 10 only
            sb.append(i + 1).append(". ").append(ok.get(i)).append("\n");
        if (ok.isEmpty()) sb.append("(no successful attempts yet)\n");
        sb.append("\nNOT DEFUSED\n");
        int from = Math.max(0, other.size() - 6);        // the 6 most recent failures
        for (int i = other.size() - 1; i >= from; i--)
            sb.append(other.get(i).name).append(" - ").append(other.get(i).outcome).append("\n");
        return sb.toString();
    }
}
