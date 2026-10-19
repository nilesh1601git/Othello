package othello.net;

import java.util.Locale;
import othello.model.Disc;
import othello.model.Game;

public final class Protocol {
  public static final int DEFAULT_PORT = 47820;
  public static final int DISCOVERY_PORT = 47821;

  private Protocol() {
  }

  public static String sanitizeName(String name) {
    if (name == null) {
      return "Player";
    }
    String cleaned = name.replaceAll("[\\r\\n|]", " ").trim();
    if (cleaned.isEmpty()) {
      return "Player";
    }
    return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
  }

  public static String hello(String name) {
    return "HELLO " + sanitizeName(name);
  }

  public static String welcome(Disc you, String opponentName) {
    return "WELCOME " + symbol(you) + " " + sanitizeName(opponentName);
  }

  public static String state(Game game) {
    return "STATE " + game.encodeBoard()
        + " " + symbol(game.turn())
        + " " + (game.isFinished() ? "1" : "0")
        + " " + game.lastRow()
        + " " + game.lastCol()
        + " " + (game.opponentPassed() ? "1" : "0");
  }

  public static String move(int row, int col) {
    return "MOVE " + row + " " + col;
  }

  public static String end(String reason) {
    return "END " + reason.replace('\n', ' ').replace('\r', ' ').trim();
  }

  public static String bye() {
    return "BYE";
  }

  public static Message parse(String line) {
    if (line == null) {
      return new Unknown("");
    }
    String trimmed = line.trim();
    if (trimmed.isEmpty() || trimmed.length() > 500) {
      return new Unknown(trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed);
    }
    int space = trimmed.indexOf(' ');
    String verb = (space < 0 ? trimmed : trimmed.substring(0, space)).toUpperCase(Locale.ROOT);
    String rest = space < 0 ? "" : trimmed.substring(space + 1).trim();
    try {
      return switch (verb) {
        case "HELLO" -> new Hello(sanitizeName(rest));
        case "WELCOME" -> parseWelcome(rest);
        case "STATE" -> parseState(rest);
        case "MOVE" -> parseMove(rest);
        case "END" -> new End(rest.isEmpty() ? "Game over" : rest);
        case "BYE" -> new Bye();
        default -> new Unknown(trimmed);
      };
    } catch (RuntimeException ex) {
      return new Unknown(trimmed);
    }
  }

  private static Message parseWelcome(String rest) {
    String[] parts = rest.split(" ", 2);
    if (parts.length < 2) {
      return new Unknown("WELCOME " + rest);
    }
    return new Welcome(disc(parts[0]), sanitizeName(parts[1]));
  }

  private static Message parseState(String rest) {
    String[] parts = rest.split(" ");
    if (parts.length != 6 || parts[0].length() != 64) {
      return new Unknown("STATE");
    }
    return new State(
        parts[0],
        disc(parts[1]),
        "1".equals(parts[2]),
        Integer.parseInt(parts[3]),
        Integer.parseInt(parts[4]),
        "1".equals(parts[5]));
  }

  private static Message parseMove(String rest) {
    String[] parts = rest.split(" ");
    if (parts.length != 2) {
      return new Unknown("MOVE " + rest);
    }
    int row = Integer.parseInt(parts[0]);
    int col = Integer.parseInt(parts[1]);
    if (row < 0 || col < 0 || row > 7 || col > 7) {
      return new Unknown("MOVE " + rest);
    }
    return new Move(row, col);
  }

  private static String symbol(Disc disc) {
    return disc == Disc.BLACK ? "B" : "W";
  }

  private static Disc disc(String symbol) {
    return switch (symbol) {
      case "B" -> Disc.BLACK;
      case "W" -> Disc.WHITE;
      default -> throw new IllegalArgumentException(symbol);
    };
  }

  public sealed interface Message permits Hello, Welcome, State, Move, End, Bye, Unknown {
  }

  public record Hello(String name) implements Message {
  }

  public record Welcome(Disc you, String opponentName) implements Message {
  }

  public record State(String grid, Disc turn, boolean finished, int lastRow, int lastCol,
      boolean opponentPassed) implements Message {
    public Game game() {
      Game game = Game.sync(grid, turn, finished, lastRow, lastCol, opponentPassed);
      return game;
    }
  }

  public record Move(int row, int col) implements Message {
  }

  public record End(String reason) implements Message {
  }

  public record Bye() implements Message {
  }

  public record Unknown(String raw) implements Message {
  }
}
