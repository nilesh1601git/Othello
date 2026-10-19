package othello.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One Othello game. After every move the turn advances, and a player with no
 * legal move passes automatically. The game ends when neither player can move.
 */
public final class Game {
  private Board board;
  private Disc turn;
  private boolean finished;
  private int lastRow = -1;
  private int lastCol = -1;
  private boolean opponentPassed;
  private String endReason;
  private final Deque<Memento> history = new ArrayDeque<>();

  public Game() {
    this(new Board(), Disc.BLACK, false, -1, -1, false, null);
  }

  private Game(Board board, Disc turn, boolean finished, int lastRow, int lastCol,
      boolean opponentPassed, String endReason) {
    this.board = board;
    this.turn = turn;
    this.finished = finished;
    this.lastRow = lastRow;
    this.lastCol = lastCol;
    this.opponentPassed = opponentPassed;
    this.endReason = endReason;
  }

  public static Game custom(Board board, Disc toMove) {
    Game game = new Game(board.copy(), toMove, false, -1, -1, false, null);
    game.skipIfNoMove();
    return game;
  }

  public static Game sync(String grid, Disc turn, boolean finished, int lastRow, int lastCol,
      boolean opponentPassed) {
    return new Game(Board.parse(grid), turn, finished, lastRow, lastCol, opponentPassed, null);
  }

  public Game snapshot() {
    return new Game(board.copy(), turn, finished, lastRow, lastCol, opponentPassed, endReason);
  }

  public Board boardCopy() {
    return board.copy();
  }

  public String encodeBoard() {
    return board.encode();
  }

  public Disc turn() {
    return turn;
  }

  public boolean isFinished() {
    return finished;
  }

  public int lastRow() {
    return lastRow;
  }

  public int lastCol() {
    return lastCol;
  }

  public boolean opponentPassed() {
    return opponentPassed;
  }

  public String endReason() {
    return endReason;
  }

  public int count(Disc disc) {
    return board.count(disc);
  }

  public boolean canUndo() {
    return endReason == null && !history.isEmpty();
  }

  public boolean play(int row, int col) {
    if (finished || endReason != null || !board.isLegal(row, col, turn)) {
      return false;
    }
    history.push(memento());
    board.apply(row, col, turn);
    lastRow = row;
    lastCol = col;
    Disc opponent = turn.opponent();
    if (board.hasMove(opponent)) {
      turn = opponent;
      opponentPassed = false;
      return true;
    }
    if (board.hasMove(turn)) {
      opponentPassed = true;
      return true;
    }
    finished = true;
    opponentPassed = false;
    return true;
  }

  public boolean undo() {
    if (!canUndo()) {
      return false;
    }
    restore(history.pop());
    return true;
  }

  public void finish(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A finish reason is required");
    }
    if (finished) {
      return;
    }
    finished = true;
    endReason = reason;
  }

  private void skipIfNoMove() {
    if (board.hasMove(turn)) {
      return;
    }
    if (board.hasMove(turn.opponent())) {
      turn = turn.opponent();
      opponentPassed = true;
      return;
    }
    finished = true;
  }

  private Memento memento() {
    return new Memento(board.encode(), turn, finished, lastRow, lastCol, opponentPassed);
  }

  private void restore(Memento memento) {
    board = Board.parse(memento.grid());
    turn = memento.turn();
    finished = memento.finished();
    lastRow = memento.lastRow();
    lastCol = memento.lastCol();
    opponentPassed = memento.opponentPassed();
    endReason = null;
  }

  private record Memento(String grid, Disc turn, boolean finished, int lastRow, int lastCol,
      boolean opponentPassed) {
  }
}
