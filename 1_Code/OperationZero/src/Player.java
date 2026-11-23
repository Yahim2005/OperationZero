/** One player: a name and an id that is handed out automatically. */
public class Player {
    private static int nextId = 1;   // shared counter so every player gets a new id
    public final int id;
    public final String name;

    public Player(String name) {
        this.id = nextId++;
        this.name = name;
    }
}
