package othello.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import othello.model.Board;
import othello.model.Disc;
import othello.model.Move;

/**
 * Alpha-beta search with a positional table. Easy mode picks a random legal move
 * so a new player can win. Medium and hard search until a depth or time limit.
 */
public final class ComputerPlayer {
  private static final int[][] WEIGHTS = {
      {120, -20, 20,  5,  5, 20, -20, 120},
      {-20, -40, -5, -5, -5, -5, -40, -20},
      { 20,  -5, 15,  3,  3, 15,  -5,  20},
      {  5,  -5,  3,  3,  3,  3,  -5,   5},
      {  5,  -5,  3,  3,  3,  3,  -5,   5},
      { 20,  -5, 15,  3,  3, 15,  -5,  20},
      {-20, -40, -5, -5, -5, -5, -40, -20},
      {120, -20, 20,  5,  5, 20, -20, 120}
  };

  public Move choose(Board board, Disc player, Difficulty difficulty) {
    return choose(board, player, difficulty, () -> false);
  }

  public Move choose(Board board, Disc player, Difficulty difficulty, BooleanSupplier cancelled) {
    List<Move> moves = new ArrayList<>(board.legalMoves(player));
    if (moves.isEmpty() || cancelled.getAsBoolean()) {
      return null;
    }
    if (difficulty == Difficulty.EASY) {
      return moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
    }
    moves.sort(Comparator.comparingInt(move -> priority(board, move)));
    Move best = moves.get(0);
    long deadline = System.nanoTime() + difficulty.budgetMillis() * 1_000_000L;
    for (int depth = 1; depth <= difficulty.maxDepth(); depth++) {
      try {
        best = bestAtDepth(board, player, moves, depth, deadline, cancelled);
        Move chosen = best;
        moves.remove(chosen);
        moves.add(0, chosen);
      } catch (SearchExpired expired) {
        break;
      }
    }
    return best;
  }

  private Move bestAtDepth(Board board, Disc player, List<Move> moves, int depth, long deadline,
      BooleanSupplier cancelled) {
    Move bestMove = moves.get(0);
    int bestValue = Integer.MIN_VALUE;
    int alpha = Integer.MIN_VALUE;
    for (Move move : moves) {
      expireIfNeeded(deadline, cancelled);
      Board next = board.copy();
      next.apply(move.row(), move.col(), player);
      int value = minimax(next, player.opponent(), player, depth - 1, alpha, Integer.MAX_VALUE,
          deadline, cancelled);
      if (value > bestValue) {
        bestValue = value;
        bestMove = move;
        alpha = value;
      }
    }
    return bestMove;
  }

  private int minimax(Board board, Disc toMove, Disc me, int depth, int alpha, int beta,
      long deadline, BooleanSupplier cancelled) {
    expireIfNeeded(deadline, cancelled);
    boolean canMove = board.hasMove(toMove);
    boolean opponentCanMove = board.hasMove(toMove.opponent());
    if (!canMove && !opponentCanMove) {
      return terminal(board, me);
    }
    if (depth == 0) {
      return heuristic(board, me);
    }
    if (!canMove) {
      return minimax(board, toMove.opponent(), me, depth - 1, alpha, beta, deadline, cancelled);
    }
    boolean maximizing = toMove == me;
    int best = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    List<Move> moves = new ArrayList<>(board.legalMoves(toMove));
    moves.sort(Comparator.comparingInt(move -> priority(board, move)));
    for (Move move : moves) {
      expireIfNeeded(deadline, cancelled);
      Board next = board.copy();
      next.apply(move.row(), move.col(), toMove);
      int value = minimax(next, toMove.opponent(), me, depth - 1, alpha, beta, deadline, cancelled);
      if (maximizing) {
        best = Math.max(best, value);
        alpha = Math.max(alpha, value);
      } else {
        best = Math.min(best, value);
        beta = Math.min(beta, value);
      }
      if (beta <= alpha) {
        break;
      }
    }
    return best;
  }

  private static int terminal(Board board, Disc me) {
    int diff = board.count(me) - board.count(me.opponent());
    if (diff > 0) {
      return 100_000 + diff;
    }
    if (diff < 0) {
      return -100_000 + diff;
    }
    return 0;
  }

  private static int heuristic(Board board, Disc me) {
    Disc opponent = me.opponent();
    int positional = 0;
    for (int row = 0; row < Board.SIZE; row++) {
      for (int col = 0; col < Board.SIZE; col++) {
        Disc disc = board.get(row, col);
        if (disc == me) {
          positional += WEIGHTS[row][col];
        } else if (disc == opponent) {
          positional -= WEIGHTS[row][col];
        }
      }
    }
    int mobility = board.legalMoves(me).size() - board.legalMoves(opponent).size();
    int discDiff = board.count(me) - board.count(opponent);
    int empty = board.emptyCount();
    if (empty > 20) {
      return positional + mobility * 15;
    }
    if (empty > 10) {
      return positional + mobility * 10 + discDiff * 4;
    }
    return discDiff * 25 + positional / 4 + mobility * 4;
  }

  private static int priority(Board board, Move move) {
    int row = move.row();
    int col = move.col();
    if (isCorner(row, col)) {
      return 0;
    }
    if (besideEmptyCorner(board, row, col)) {
      return 4;
    }
    if (row == 0 || row == Board.SIZE - 1 || col == 0 || col == Board.SIZE - 1) {
      return 1;
    }
    return 2;
  }

  private static boolean isCorner(int row, int col) {
    return (row == 0 || row == Board.SIZE - 1) && (col == 0 || col == Board.SIZE - 1);
  }

  private static boolean besideEmptyCorner(Board board, int row, int col) {
    int[][] corners = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    for (int[] corner : corners) {
      if (board.get(corner[0], corner[1]) != Disc.EMPTY) {
        continue;
      }
      int rowDistance = Math.abs(corner[0] - row);
      int colDistance = Math.abs(corner[1] - col);
      if (rowDistance <= 1 && colDistance <= 1 && (rowDistance + colDistance) > 0) {
        return true;
      }
    }
    return false;
  }

  private static void expireIfNeeded(long deadline, BooleanSupplier cancelled) {
    if (cancelled.getAsBoolean() || System.nanoTime() > deadline) {
      throw new SearchExpired();
    }
  }

  private static final class SearchExpired extends RuntimeException {
    SearchExpired() {
      super(null, null, false, false);
    }
  }
}
