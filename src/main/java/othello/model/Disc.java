package othello.model;

public enum Disc {
  EMPTY,
  BLACK,
  WHITE;

  public Disc opponent() {
    return switch (this) {
      case BLACK -> WHITE;
      case WHITE -> BLACK;
      case EMPTY -> EMPTY;
    };
  }

  public char symbol() {
    return switch (this) {
      case BLACK -> 'B';
      case WHITE -> 'W';
      case EMPTY -> '.';
    };
  }

  public static Disc fromSymbol(char symbol) {
    return switch (symbol) {
      case 'B', 'b' -> BLACK;
      case 'W', 'w' -> WHITE;
      case '.' -> EMPTY;
      default -> throw new IllegalArgumentException("Unknown disc: " + symbol);
    };
  }
}
