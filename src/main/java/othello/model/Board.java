package othello.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Board {
  public static final int SIZE = 8;
  private static final int[][] DIRECTIONS = {
      {-1, -1}, {-1, 0}, {-1, 1},
      {0, -1},           {0, 1},
      {1, -1},  {1, 0},  {1, 1}
  };

  private final Disc[] cells;

  public Board() {
    this(initialCells());
  }

  private Board(Disc[] cells) {
    this.cells = cells;
  }

  public static Board parse(String text) {
    StringBuilder symbols = new StringBuilder(SIZE * SIZE);
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch == 'B' || ch == 'b' || ch == 'W' || ch == 'w' || ch == '.') {
        symbols.append(Character.toUpperCase(ch));
      }
    }
    if (symbols.length() != SIZE * SIZE) {
      throw new IllegalArgumentException("Board must contain 64 squares, found " + symbols.length());
    }
    Disc[] cells = new Disc[SIZE * SIZE];
    for (int i = 0; i < cells.length; i++) {
      cells[i] = Disc.fromSymbol(symbols.charAt(i));
    }
    return new Board(cells);
  }

  public Board copy() {
    return new Board(cells.clone());
  }

  public Disc get(int row, int col) {
    return cells[index(row, col)];
  }

  public String encode() {
    StringBuilder out = new StringBuilder(SIZE * SIZE);
    for (Disc cell : cells) {
      out.append(cell.symbol());
    }
    return out.toString();
  }

  public int count(Disc disc) {
    int total = 0;
    for (Disc cell : cells) {
      if (cell == disc) {
        total++;
      }
    }
    return total;
  }

  public int emptyCount() {
    return count(Disc.EMPTY);
  }

  public boolean hasMove(Disc player) {
    return !legalMoves(player).isEmpty();
  }

  public List<Move> legalMoves(Disc player) {
    if (player == Disc.EMPTY) {
      return List.of();
    }
    List<Move> moves = new ArrayList<>();
    for (int row = 0; row < SIZE; row++) {
      for (int col = 0; col < SIZE; col++) {
        if (!flips(row, col, player).isEmpty()) {
          moves.add(new Move(row, col));
        }
      }
    }
    return moves;
  }

  public boolean isLegal(int row, int col, Disc player) {
    return inBounds(row, col) && !flips(row, col, player).isEmpty();
  }

  /**
   * Places {@code player} and flips every outflanked line.
   * Returns false when the square is not a legal move.
   */
  public boolean apply(int row, int col, Disc player) {
    List<Integer> flipped = flips(row, col, player);
    if (flipped.isEmpty()) {
      return false;
    }
    cells[index(row, col)] = player;
    for (int at : flipped) {
      cells[at] = player;
    }
    return true;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Board board)) {
      return false;
    }
    return Arrays.equals(cells, board.cells);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(cells);
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    for (int row = 0; row < SIZE; row++) {
      if (row > 0) {
        out.append('\n');
      }
      for (int col = 0; col < SIZE; col++) {
        out.append(get(row, col).symbol());
      }
    }
    return out.toString();
  }

  private List<Integer> flips(int row, int col, Disc player) {
    if (player == Disc.EMPTY || !inBounds(row, col) || get(row, col) != Disc.EMPTY) {
      return List.of();
    }
    Disc opponent = player.opponent();
    List<Integer> flipped = new ArrayList<>();
    for (int[] direction : DIRECTIONS) {
      List<Integer> line = new ArrayList<>();
      int nextRow = row + direction[0];
      int nextCol = col + direction[1];
      while (inBounds(nextRow, nextCol) && get(nextRow, nextCol) == opponent) {
        line.add(index(nextRow, nextCol));
        nextRow += direction[0];
        nextCol += direction[1];
      }
      if (!line.isEmpty() && inBounds(nextRow, nextCol) && get(nextRow, nextCol) == player) {
        flipped.addAll(line);
      }
    }
    return flipped;
  }

  private static Disc[] initialCells() {
    Disc[] cells = emptyCells();
    cells[index(3, 3)] = Disc.WHITE;
    cells[index(3, 4)] = Disc.BLACK;
    cells[index(4, 3)] = Disc.BLACK;
    cells[index(4, 4)] = Disc.WHITE;
    return cells;
  }

  private static Disc[] emptyCells() {
    Disc[] cells = new Disc[SIZE * SIZE];
    Arrays.fill(cells, Disc.EMPTY);
    return cells;
  }

  private static boolean inBounds(int row, int col) {
    return row >= 0 && col >= 0 && row < SIZE && col < SIZE;
  }

  private static int index(int row, int col) {
    if (!inBounds(row, col)) {
      throw new IllegalArgumentException("Square out of range: " + row + "," + col);
    }
    return row * SIZE + col;
  }
}
