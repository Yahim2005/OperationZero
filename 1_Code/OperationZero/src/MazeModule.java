import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mini-game 4, "Ghost Hunt" (joystick + buzzer + screen): a Pac-Man game on the screen.
 * Eat every pellet and dodge the ghost. The buzzer beeps faster the closer the ghost is.
 * Touching the ghost is a strike and both characters go back to their start.
 * The maze is a small grid: wall[][] marks walls, pellet[][] marks food.
 */
public class MazeModule implements BombModule {

    private static final int ROWS = 4, COLS = 28;
    private static final int[] BARS = {6, 13, 20};          // walls that cut the maze into rooms
    private static final int[] GAPS = {3, 0, 3};            // the row where each wall has a door
    private static final int[] PILLARS = {3, 10, 17, 24};   // little blocks in the middle of each room

    private static final int JX_SIGN = 1;                   // flip to -1 if left and right are reversed
    private static final int JY_SIGN = -1;                  // flip to 1 if up and down are reversed
    private static final int PUSH = 250;                    // counts from the stick's resting position
    private static final long PAC_MS = 250;                 // Pac-Man moves one cell this often
    private static final long GHOST_MS = 800;               // the ghost is slower
    private static final long SAFE_MS = 2000;               // peace after a collision
    private static final int PELLETS = 4;

    private final boolean[][] wall = new boolean[ROWS][COLS];      // true = wall

    private final boolean[][] pellet = new boolean[ROWS][COLS];
    private final long seed;                                // same seed = same pellets after a reset
    private int pr, pc, gr, gc;                             // Pac-Man and ghost position
    private char face = '>';                                // direction Pac-Man is facing
    private int eaten;
    private long lastPac, lastGhost, lastBeep, safeUntil;
    private Random rnd;
    private boolean complete;
    private String status;

    public MazeModule(long seed) {
        this.seed = seed;
        // build the walls: each bar is a full column with one door row
        for (int c : BARS) {
            int gap = GAPS[java.util.Arrays.binarySearch(BARS, c)];
            for (int r = 0; r < ROWS; r++) wall[r][c] = r != gap;
        }
        // pillars make the rooms less empty
        for (int c : PILLARS) {
            wall[1][c] = true;
            wall[2][c] = true;
        }
        reset();
    }

    public String getName() { return "Ghost Hunt"; }

    public String getInstruction() { return "JOYSTICK: EAT EVERY PELLET, DODGE THE GHOST"; }

    // puts everything back at the start (also used for a new attempt)
    public void reset() {
        rnd = new Random(seed);
        pr = 1; pc = 0;
        gr = 2; gc = COLS - 1;
        face = '>';
        eaten = 0;
        complete = false;
        lastPac = lastGhost = lastBeep = 0;
        safeUntil = 0;
        for (boolean[] row : pellet) java.util.Arrays.fill(row, false);
        // one pellet in each room
        int[][] rooms = {{1, 5}, {7, 12}, {14, 19}, {21, 26}};
        for (int[] room : rooms) {
            while (true) {
                int r = rnd.nextInt(ROWS), c = room[0] + rnd.nextInt(room[1] - room[0] + 1);
                if (!wall[r][c] && !pellet[r][c]) { pellet[r][c] = true; break; }
            }
        }
        status = "PELLETS 0/" + PELLETS;
    }

    public ModuleResult processInput(SensorEvent e, Calibration cal) {
        if (complete || e.type != SensorEvent.SAMPLE) return ModuleResult.NONE;
        long t = e.time;   // sensor samples drive the game clock
        if (lastPac == 0) { lastPac = t; lastGhost = t; }
        ModuleResult result = ModuleResult.NONE;

        // ---- move Pac-Man one cell, at most once every PAC_MS ----
        if (t - lastPac >= PAC_MS) {
            lastPac = t;
            int x = JX_SIGN * (e.jx - cal.jx0);
            int y = JY_SIGN * (e.jy - cal.jy0);
            // the stronger axis decides the direction (dr = row step, dc = column step)
            int dr = 0, dc = 0;
            if (Math.abs(x) >= PUSH && Math.abs(x) >= Math.abs(y)) { dc = x < 0 ? -1 : 1; face = x < 0 ? '<' : '>'; }
            else if (Math.abs(y) >= PUSH) { dr = y > 0 ? -1 : 1; face = y > 0 ? '^' : 'v'; }
            // move only if the target cell is inside the maze and is not a wall
            int nr = pr + dr, nc = pc + dc;
            if ((dr != 0 || dc != 0) && nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && !wall[nr][nc]) {
                pr = nr;
                pc = nc;
                if (pellet[pr][pc]) {
                    pellet[pr][pc] = false;
                    eaten++;
                    Sound.get().beep(880, 50);                // chomp
                    status = "PELLETS " + eaten + "/" + PELLETS;
                    if (eaten >= PELLETS) {
                        complete = true;
                        status = "ALL PELLETS EATEN!";
                        return ModuleResult.COMPLETE;
                    }
                }
            }
        }
        // ---- the ghost moves more slowly ----
        if (t - lastGhost >= GHOST_MS) {
            lastGhost = t;
            moveGhost();
        }
        // ---- collision: strike, both characters restart, short safe period ----
        if (pr == gr && pc == gc && t >= safeUntil) {
            pr = 1; pc = 0;
            gr = 2; gc = COLS - 1;
            safeUntil = t + SAFE_MS;
            status = "CAUGHT BY THE GHOST!";
            result = ModuleResult.STRIKE;
        } else {
            // Manhattan distance to the ghost drives the warning beeps
            int d = Math.abs(pr - gr) + Math.abs(pc - gc);
            if (d <= 5 && t - lastBeep >= d * 120 + 100) {   // the closer the ghost, the faster the beeps
                lastBeep = t;
                Sound.get().beep(300 + (6 - d) * 80, 40);
            }
            status = "PELLETS " + eaten + "/" + PELLETS + (d <= 4 ? "  GHOST CLOSE!" : "");
        }
        return result;
    }

    // the ghost steps towards Pac-Man along the shortest path, but sometimes wanders
    private void moveGhost() {
        int[][] dist = distancesFromPac();   // shortest distance from every cell to Pac-Man
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};   // up, down, left, right
        List<int[]> best = new ArrayList<int[]>(), all = new ArrayList<int[]>();
        int bestDist = Integer.MAX_VALUE;
        for (int k = 0; k < 4; k++) {
            int nr = gr + dr[k], nc = gc + dc[k];
            if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS || wall[nr][nc]) continue;
            all.add(new int[]{nr, nc});
            if (dist[nr][nc] < bestDist) { bestDist = dist[nr][nc]; best.clear(); }
            if (dist[nr][nc] == bestDist) best.add(new int[]{nr, nc});
        }
        // 65% of the time take a best step, otherwise any open step (so it can be dodged)
        List<int[]> pick = rnd.nextInt(100) < 65 ? best : all;
        int[] step = pick.get(rnd.nextInt(pick.size()));
        gr = step[0];
        gc = step[1];
    }

    // breadth-first search from Pac-Man's cell over the open cells
    private int[][] distancesFromPac() {
        int[][] dist = new int[ROWS][COLS];
        for (int[] row : dist) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        List<int[]> queue = new ArrayList<int[]>();
        queue.add(new int[]{pr, pc});
        dist[pr][pc] = 0;
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        for (int i = 0; i < queue.size(); i++) {
            int[] cur = queue.get(i);
            for (int k = 0; k < 4; k++) {
                int nr = cur[0] + dr[k], nc = cur[1] + dc[k];
                if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS || wall[nr][nc]) continue;
                if (dist[nr][nc] != Integer.MAX_VALUE) continue;
                dist[nr][nc] = dist[cur[0]][cur[1]] + 1;
                queue.add(new int[]{nr, nc});
            }
        }
        return dist;
    }

    // '#' wall, 'o' pellet, 'G' ghost, '>' '<' '^' 'v' Pac-Man, ' ' empty
    public String[] getScene() {
        String[] rows = new String[ROWS];
        for (int r = 0; r < ROWS; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < COLS; c++) {
                char ch = wall[r][c] ? '#' : pellet[r][c] ? 'o' : ' ';
                if (r == gr && c == gc) ch = 'G';
                if (r == pr && c == pc) ch = face;
                sb.append(ch);
            }
            rows[r] = sb.toString();
        }
        return rows;
    }

    public boolean isComplete() { return complete; }
    public int getProgress() { return complete ? 100 : eaten * 100 / PELLETS; }
    public String getStatus() { return status; }
}
