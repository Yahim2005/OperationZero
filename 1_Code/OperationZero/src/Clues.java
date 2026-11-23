import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The text content of the game: the question bank for Security Authorization (every
 * answer is a colour) and the clue bank for Override Code (every answer is a number
 * the player must work out). Nothing here talks to the hardware.
 */
public class Clues {

    // "" = every keypad key works.
    // If a keypad column is dead, list its digits here (for column 2: "2580") and the game
    // will only pick codes that do not need those keys.
    public static final String DEAD_DIGITS = "";

    // QUESTIONS[colour][n]  -  colour 0 = RED, 1 = BLUE, 2 = GREEN (same order as the card names)
    public static final String[][] QUESTIONS = {
        {   // RED
            "Which colour do your cheeks turn when you are embarrassed?",
            "Mars is nicknamed the ___ Planet. Which colour?",
            "Which colour means STOP at a traffic light?"
        },
        {   // BLUE
            "On a clear day at noon, which colour is the sky?",
            "Which colour is a Smurf?",
            "The biggest animal ever is the ___ whale. Which colour?"
        },
        {   // GREEN
            "The Hulk turns this colour when he gets angry.",
            "Which traffic-light colour means GO?",
            "The left stripe of the Cameroon flag is which colour?"
        }
    };

    // {clue, code}.  Birthdays are written day then month (DDMM).
    private static final String[][] CODES = {
        {"He cracked the Enigma code and gave his name to a famous test for machines. Code = his birthday, DDMM.", "2306"},
        {"Argentine number 10 who lifted the 2022 World Cup. Code = his birthday, DDMM.", "2406"},
        {"CR7: the Portuguese goal machine. Code = his birthday, DDMM.", "0502"},
        {"The Cameroonian legend who danced at the corner flag in 1990: Roger Milla. Code = his birthday, DDMM.", "2005"},
        {"The Pharaohs' pride, the Egyptian King: Mohamed Salah. Code = his birthday, DDMM.", "1506"},
        {"E = mc squared. Code = the birthday of the man who wrote it, DDMM.", "1403"},
        {"The King of Pop (moonwalk included). Code = his birthday, DDMM.", "2908"},
        {"He put a thousand songs in your pocket. Code = the birthday of Apple's co-founder Steve Jobs, DDMM.", "2402"},
        // years that only use the keys 1 3 4 6 7 9 (they work even with a dead column 2)
        {"Code = the year the seven emirates became one country: the UAE.", "1971"},
        {"Code = the year the American University of Sharjah was founded.", "1997"},
        {"Code = the year of the first steps on the Moon.", "1969"},
        {"Code = the year Yuri Gagarin became the first human in space.", "1961"},
        {"Code = the year Linus Torvalds released the first Linux.", "1991"},
        {"Code = the year Apple was founded.", "1976"},
        {"Code = the year the transistor was invented at Bell Labs.", "1947"},
        {"Code = the year the ENIAC, a giant early computer, was unveiled.", "1946"}
    };

    // picks one random question whose answer is the given colour
    public static String question(Random r, int colour) {
        String[] q = QUESTIONS[colour];
        return q[r.nextInt(q.length)];
    }

    // returns {clue, code}: a random clue whose code only uses keys that work
    public static String[] codeClue(Random r) {
        List<String[]> usable = new ArrayList<String[]>();
        for (String[] c : CODES)
            if (canType(c[1])) usable.add(c);
        return usable.get(r.nextInt(usable.size()));
    }

    // false if the code needs a digit that is listed as dead
    private static boolean canType(String code) {
        for (int i = 0; i < DEAD_DIGITS.length(); i++)
            if (code.indexOf(DEAD_DIGITS.charAt(i)) >= 0) return false;
        return true;
    }
}
