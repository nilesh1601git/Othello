package othello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import othello.model.Board;
import othello.model.Disc;
import othello.model.Game;
import othello.model.Move;
import org.junit.jupiter.api.Test;

class RulesTest {
  @Test
  void openingPositionHasFourBlackMoves() {
    Board board = new Board();
    assertEquals(2, board.count(Disc.BLACK));
    assertEquals(2, board.count(Disc.WHITE));
    assertEquals(Disc.WHITE, board.get(3, 3));
    assertEquals(Disc.BLACK, board.get(3, 4));
    assertEquals(Disc.BLACK, board.get(4, 3));
    assertEquals(Disc.WHITE, board.get(4, 4));
    List<Move> moves = board.legalMoves(Disc.BLACK);
    assertEquals(4, moves.size());
    assertTrue(moves.contains(new Move(2, 3)));
    assertTrue(moves.contains(new Move(3, 2)));
    assertTrue(moves.contains(new Move(4, 5)));
    assertTrue(moves.contains(new Move(5, 4)));
  }

  @Test
  void moveFlipsEveryOutflankedLine() {
    Board board = Board.parse("""
        ..B.....
        BW.B....
        ..W.....
        ..B.....
        ........
        ........
        ........
        ........
        """);
    assertTrue(board.apply(1, 2, Disc.BLACK));
    assertEquals(Disc.BLACK, board.get(1, 1));
    assertEquals(Disc.BLACK, board.get(2, 2));
    assertEquals(Disc.BLACK, board.get(1, 2));
    assertEquals(Disc.BLACK, board.get(1, 0));
    assertEquals(Disc.BLACK, board.get(1, 3));
    assertEquals(Disc.EMPTY, board.get(0, 0));
  }

  @Test
  void illegalMoveDoesNotChangeTheBoard() {
    Board board = new Board();
    Board before = board.copy();
    assertFalse(board.apply(0, 0, Disc.BLACK));
    assertEquals(before, board);
  }

  @Test
  void playPassesWhenTheOpponentHasNoMove() {
    String row = "BBBBBBBB";
    Board board = Board.parse(".WBBBBBB" + row + ".WBBBBBB" + row.repeat(5));
    Game game = Game.custom(board, Disc.BLACK);
    assertTrue(game.play(0, 0));
    assertFalse(game.isFinished());
    assertTrue(game.opponentPassed());
    assertEquals(Disc.BLACK, game.turn());
    assertEquals(Disc.BLACK, game.boardCopy().get(0, 1));
    assertTrue(game.boardCopy().isLegal(2, 0, Disc.BLACK));
  }

  @Test
  void gameEndsWhenNeitherSideCanMove() {
    Game game = Game.custom(Board.parse("W".repeat(63) + "."), Disc.BLACK);
    assertTrue(game.isFinished());
    assertFalse(game.play(7, 7));
  }

  @Test
  void undoRestoresThePreviousPosition() {
    Game game = new Game();
    assertTrue(game.play(2, 3));
    assertEquals(4, game.count(Disc.BLACK));
    assertEquals(1, game.count(Disc.WHITE));
    assertTrue(game.undo());
    assertEquals(Disc.BLACK, game.turn());
    assertEquals(2, game.count(Disc.BLACK));
    assertEquals(2, game.count(Disc.WHITE));
    assertEquals(-1, game.lastRow());
  }
}
