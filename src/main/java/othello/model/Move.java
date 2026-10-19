package othello.model;

public record Move(int row, int col) {
  public Move {
    if (row < 0 || col < 0 || row >= Board.SIZE || col >= Board.SIZE) {
      throw new IllegalArgumentException("Square out of range: " + row + "," + col);
    }
  }

  @Override
  public String toString() {
    return "" + (char) ('a' + col) + (row + 1);
  }
}
