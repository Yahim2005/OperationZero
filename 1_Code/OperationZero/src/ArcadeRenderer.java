import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

/**
 * Draws the whole game screen in a tiny 420 x 240 picture with only four colours,
 * like a 2-bit arcade game (Pac-Man style). ArcadePanel then scales it up with sharp
 * pixels. Everything is drawn from a Snapshot, so this class has no game logic.
 */
public class ArcadeRenderer {

    public static final int W = 420, H = 240;

    // the four colours of the screen (change them here to restyle the whole game)
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color BLUE = new Color(33, 33, 255);
    public static final Color YELLOW = new Color(255, 255, 0);
    public static final Color WHITE = new Color(255, 255, 255);

    private static final int GAME_W = 300;           // width of the game area (the rest is the leaderboard)

    // draws one full frame; "now" drives all the blinking and animation
    public static void render(Graphics2D g, Snapshot s, long now) {
        boolean blink = (now / 400) % 2 == 0;
        g.setColor(BLACK);
        g.fillRect(0, 0, W, H);
        drawFrame(g);

        // title with a ghost on each side
        BitmapFont.drawCentered(g, "OPERATION ZERO", GAME_W / 2, 8, 2, YELLOW);
        drawGhost(g, 14, 7, 14, WHITE, (int) (now / 250));
        drawGhost(g, GAME_W - 28, 7, 14, WHITE, (int) (now / 250) + 1);

        // player name on the left, strikes (little ghosts) on the right
        String who = s.player.isEmpty() ? "PLAYER 1" : s.player;
        if (who.length() > 16) who = who.substring(0, 16);
        BitmapFont.draw(g, who, 12, 27, 1, WHITE);
        drawStrikes(g, s.strikes, now);

        // countdown: mm:ss, flashes when under 30 s (faster under 10 s)
        int total = (int) (s.remainingMs / 1000);
        String clock = String.format("%02d:%02d", total / 60, total % 60);
        boolean running = s.state == GameState.RUNNING || s.state == GameState.READY_TO_DEFUSE;
        Color clockColor = WHITE;
        if (running && s.remainingMs < 30000) clockColor = (now / (s.remainingMs < 10000 ? 200 : 500)) % 2 == 0 ? YELLOW : WHITE;
        BitmapFont.drawCentered(g, clock, GAME_W / 2, 38, 4, clockColor);

        // state banner and message
        BitmapFont.drawCentered(g, banner(s.state), GAME_W / 2, 68, 1,
                (blink || s.state == GameState.DEFUSED) ? YELLOW : BLACK);
        // message under the banner (wrapped to at most 2 lines)
        int y = 78;
        List<String> msg = BitmapFont.wrap(s.message, 46);
        for (int i = 0; i < msg.size() && i < 2; i++, y += 9)
            BitmapFont.drawCentered(g, msg.get(i), GAME_W / 2, y, 1, WHITE);

        // instruction box (or the maze of a screen game)
        boolean hasScene = running && s.scene != null;
        g.setColor(BLUE);
        if (hasScene) {
            g.drawRect(8, 98, GAME_W - 17, 56);
            String one = s.instruction.length() > 45 ? s.instruction.substring(0, 45) : s.instruction;
            BitmapFont.drawCentered(g, one, GAME_W / 2 - 1, 101, 1, WHITE);
            drawMaze(g, s.scene, 10, 111, now);
        } else {
            g.drawRect(8, 99, GAME_W - 17, 50);
            String text = s.instruction.isEmpty()
                    ? "RULES: DEFUSE EVERY STEP BEFORE THE TIMER HITS ZERO. SHAKING THE BOMB IS A STRIKE (+5 SECONDS)."
                    : s.instruction;
            List<String> lines = BitmapFont.wrap(text, 45);
            for (int i = 0; i < lines.size() && i < 5; i++)
                BitmapFont.draw(g, lines.get(i), 14, 104 + i * 9, 1, s.instruction.isEmpty() ? BLUE : WHITE);
        }

        // live status of the active step
        if (s.active >= 0 && s.active < s.status.length && running && !s.status[s.active].isEmpty()) {
            String st = s.status[s.active];
            if (st.length() > 44) st = st.substring(0, 44);
            BitmapFont.draw(g, "> " + st, 12, hasScene ? 158 : 153, 1, YELLOW);
        }

        drawModules(g, s, now, hasScene ? 168 : 165);   // list of mini-games with progress
        drawStability(g, s, now);                         // shake bar at the bottom
        drawLeaderboard(g, s.leaderboardText);            // high scores on the right

        // big banner when the attempt is over
        if (s.state == GameState.DEFUSED || s.state == GameState.FAILED)
            drawEndBanner(g, s.state == GameState.DEFUSED, blink);
    }

    // short text that shows the state of the game
    private static String banner(GameState st) {
        switch (st) {
            case READY: return "READY!";
            case WAITING_DEVICE: return "CONNECTING...";
            case CALIBRATING: return "KEEP IT STILL AND FLAT...";
            case RUNNING: return "GO!";
            case READY_TO_DEFUSE: return "PRESS THE DEFUSE BUTTON!";
            case DEFUSED: return "YOU WIN!";
            case FAILED: return "GAME OVER";
            default: return "INTERRUPTED";
        }
    }

    // blue double border and the divider line before the leaderboard
    private static void drawFrame(Graphics2D g) {
        g.setColor(BLUE);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(2, 2, W - 5, H - 5, 12, 12);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(6, 6, W - 13, H - 13, 8, 8);
        g.drawLine(GAME_W + 2, 10, GAME_W + 2, H - 11);
    }

    // one little ghost per strike (up to 5, then "+n")
    private static void drawStrikes(Graphics2D g, int strikes, long now) {
        int right = GAME_W - 12;                       // everything is placed from the right edge
        int shown = Math.min(strikes, 5);
        int x = right - 12 * shown;
        for (int i = 0; i < shown; i++)
            drawGhost(g, right - 12 * (i + 1), 25, 10, WHITE, (int) (now / 250) + i);
        if (strikes > 5) {
            String more = "+" + (strikes - 5);
            x -= BitmapFont.width(more, 1) + 4;
            BitmapFont.draw(g, more, x, 27, 1, YELLOW);
        } else if (strikes == 0) {
            x -= 10;
            BitmapFont.draw(g, "0", x + 4, 27, 1, WHITE);
        }
        BitmapFont.draw(g, "STRIKES", x - BitmapFont.width("STRIKES", 1) - 6, 27, 1, WHITE);
    }

    // one row per mini-game: done ones say CLEAR, the active one shows Pac-Man eating pellets
    private static void drawModules(Graphics2D g, Snapshot s, long now, int y0) {
        int n = s.moduleNames.length;
        if (n == 0) return;
        int rowH = Math.min(11, 54 / n);
        for (int i = 0; i < n; i++) {
            int y = y0 + i * rowH;
            boolean done = i < s.active || s.progress[i] >= 100;
            boolean active = i == s.active && !done;
            Color nameColor = done ? BLUE : active ? YELLOW : WHITE;
            String name = s.moduleNames[i].toUpperCase();
            if (name.length() > 22) name = name.substring(0, 22);
            if (active && (now / 300) % 2 == 0) BitmapFont.draw(g, ">", 8, y, 1, YELLOW);
            BitmapFont.draw(g, (i + 1) + " " + name, 16, y, 1, nameColor);

            // the track: pellets that Pac-Man eats as the step progresses
            int x0 = 170, x1 = 286;
            if (done) {
                BitmapFont.draw(g, "CLEAR", x0, y, 1, YELLOW);
                continue;
            }
            int pellets = 16;
            double p = Math.max(0, Math.min(100, s.progress[i])) / 100.0;
            int pacX = x0 + (int) (p * (x1 - x0));
            for (int k = 0; k < pellets; k++) {
                int px = x0 + k * (x1 - x0) / (pellets - 1);
                if (px > pacX + 2 || !active && p == 0) {
                    if (k == pellets - 1) {
                        if ((now / 300) % 2 == 0) g.setColor(YELLOW); else g.setColor(BLACK);
                        g.fillOval(px - 3, y, 7, 7);
                    } else {
                        g.setColor(WHITE);
                        g.fillRect(px, y + 3, 2, 2);
                    }
                }
            }
            if (active) drawPac(g, pacX, y + 3, 4, now);
        }
    }

    // 20-segment bar: fills up as the bomb is shaken, turns yellow and flashes near the limit
    private static void drawStability(Graphics2D g, Snapshot s, long now) {
        int y = 225;
        boolean live = s.state == GameState.RUNNING || s.state == GameState.READY_TO_DEFUSE;
        BitmapFont.draw(g, "STABILITY", 12, y, 1, live && s.stability >= 60 ? YELLOW : WHITE);
        int x0 = 76, segs = 20, segW = 10;
        int lit = live ? (int) Math.round(s.stability / 100.0 * segs) : 0;
        boolean flash = live && s.stability >= 90 && (now / 150) % 2 == 0;
        for (int i = 0; i < segs; i++) {
            int x = x0 + i * segW;
            if (i < lit) g.setColor(flash ? BLACK : (i >= 12 ? YELLOW : WHITE));
            else g.setColor(BLUE);
            if (i < lit) g.fillRect(x, y, segW - 2, 7);
            else g.drawRect(x, y, segW - 3, 6);
        }
    }

    // reads the leaderboard text and redraws it in a compact two-column form
    private static void drawLeaderboard(Graphics2D g, String text) {
        int cx = GAME_W + 2 + (W - GAME_W - 2) / 2;
        BitmapFont.drawCentered(g, "HIGH SCORES", cx, 14, 1, YELLOW);
        g.setColor(BLUE);
        g.drawLine(GAME_W + 10, 25, W - 12, 25);
        int x = GAME_W + 10, y = 31;
        boolean second = false;
        for (String line : text.split("\n")) {
            if (y > H - 14) break;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("LEADERBOARD")) continue;
            if (line.equals("NOT DEFUSED")) {
                second = true;
                y += 4;
                BitmapFont.draw(g, "GAME OVER LIST", x, y, 1, BLUE);
                y += 11;
                continue;
            }
            if (line.startsWith("(no ")) {
                BitmapFont.draw(g, "NO WINNERS YET", x, y, 1, WHITE);
                y += 10;
                continue;
            }
            // top list lines look like "1. Sam  104.2s + 2 strikes = 114.2s": pick out the name and the adjusted time
            String out;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("^(\\d+)\\. (.+?)\\s+[\\d.]+s \\+ (\\d+) strikes = ([\\d.]+)s.*$").matcher(line);
            if (!second && m.matches()) {
                String name = trim(m.group(2), 8);
                String time = m.group(4) + "S";
                out = m.group(1) + " " + name;
                while (out.length() + time.length() < 17) out += " ";
                out += time;
                BitmapFont.draw(g, out, x, y, 1, Integer.parseInt(m.group(1)) == 1 ? YELLOW : WHITE);
            } else {
                String[] p = line.split(" - ");
                String what = p.length > 1 ? (p[1].equals("FAILED") ? "BOOM" : "STOP") : "";
                out = trim(p[0], 11) + " " + what;
                BitmapFont.draw(g, out, x, y, 1, WHITE);
            }
            y += 10;
        }
    }

    // cuts a text to at most n characters
    private static String trim(String s, int n) {
        return s.length() > n ? s.substring(0, n) : s;
    }

    // framed message in the middle of the screen at the end of the game
    private static void drawEndBanner(Graphics2D g, boolean win, boolean blink) {
        int cx = GAME_W / 2, cy = 128;
        g.setColor(BLACK);
        g.fillRect(cx - 120, cy - 22, 240, 44);
        g.setColor(win ? YELLOW : WHITE);
        g.drawRect(cx - 120, cy - 22, 239, 43);
        BitmapFont.drawCentered(g, win ? "BOMB DEFUSED!" : "BOOM!", cx, cy - 12, 3, blink ? (win ? YELLOW : WHITE) : BLUE);
        BitmapFont.drawCentered(g, win ? "PRESS START TO PLAY AGAIN" : "PRESS START TO TRY AGAIN", cx, cy + 12, 1, WHITE);
    }

    // draws the maze of the Ghost Hunt game: 10 x 10 pixel cells starting at (x0, y0)
    private static void drawMaze(Graphics2D g, String[] scene, int x0, int y0, long now) {
        for (int r = 0; r < scene.length; r++) {
            for (int c = 0; c < scene[r].length(); c++) {
                char ch = scene[r].charAt(c);
                int x = x0 + c * 10, y = y0 + r * 10;
                switch (ch) {
                    case '#':
                        // a wall piece: a block that joins up with the walls next to it
                        g.setColor(BLUE);
                        g.fillRect(x + 2, y + 2, 6, 6);
                        if (c + 1 < scene[r].length() && scene[r].charAt(c + 1) == '#') g.fillRect(x + 8, y + 2, 4, 6);
                        if (r + 1 < scene.length && scene[r + 1].charAt(c) == '#') g.fillRect(x + 2, y + 8, 6, 4);
                        break;
                    case 'o':
                        g.setColor(WHITE);
                        g.fillRect(x + 4, y + 4, 2, 2);
                        break;
                    case 'G':
                        drawGhost(g, x + 1, y + 1, 8, WHITE, (int) (now / 250));
                        break;
                    case '>': case '<': case '^': case 'v':
                        drawPacFacing(g, x + 5, y + 5, 4, now, ch);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    // Pac-Man with the mouth towards the direction of the last move
    private static void drawPacFacing(Graphics2D g, int cx, int cy, int r, long now, char dir) {
        int mouth = (int) (Math.abs(((now / 100) % 8) - 4) * 14);
        int start = dir == '>' ? 0 : dir == '^' ? 90 : dir == '<' ? 180 : 270;
        g.setColor(YELLOW);
        g.fillArc(cx - r, cy - r, 2 * r + 1, 2 * r + 1, start + mouth / 2, 360 - mouth);
    }

    // ---------- sprites ----------

    // Pac-Man facing right, the mouth opens and closes with time
    public static void drawPac(Graphics2D g, int cx, int cy, int r, long now) {
        int mouth = (int) (Math.abs(((now / 100) % 8) - 4) * 14);     // 0 - 56 degrees
        g.setColor(YELLOW);
        g.fillArc(cx - r, cy - r, 2 * r + 1, 2 * r + 1, mouth / 2, 360 - mouth);
    }

    // a ghost: round head, wavy skirt, two eyes. size = width in pixels
    public static void drawGhost(Graphics2D g, int x, int y, int size, Color body, int frame) {
        int h = size;
        g.setColor(body);
        g.fillArc(x, y, size, size, 0, 180);
        g.fillRect(x, y + size / 2, size, h - size / 2);
        // wavy bottom: black teeth cut out of the skirt
        g.setColor(BLACK);
        int teeth = 3, tw = size / teeth;
        for (int i = 0; i < teeth; i++) {
            int tx = x + i * tw + ((frame + i) % 2 == 0 ? 0 : tw / 2) - (frame % 2 == 0 ? 0 : 0);
            int[] xs = {tx + tw / 4, tx + tw / 2, tx + 3 * tw / 4};
            int[] ys = {y + h, y + h - Math.max(2, size / 5), y + h};
            g.fillPolygon(xs, ys, 3);
        }
        // eyes
        int e = Math.max(2, size / 5);
        g.setColor(BLACK);
        g.fillRect(x + size / 5, y + size / 3, e, e + 1);
        g.fillRect(x + size - size / 5 - e, y + size / 3, e, e + 1);
        g.setColor(BLUE);
        g.fillRect(x + size / 5 + e / 2, y + size / 3 + 1, Math.max(1, e / 2), Math.max(1, e / 2));
        g.fillRect(x + size - size / 5 - e + e / 2, y + size / 3 + 1, Math.max(1, e / 2), Math.max(1, e / 2));
    }
}
