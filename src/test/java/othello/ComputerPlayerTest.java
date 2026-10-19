package othello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import othello.ai.ComputerPlayer;
import othello.ai.Difficulty;
import othello.model.Board;
import othello.model.Disc;
import othello.model.Game;
import othello.model.Move;
import org.junit.jupiter.api.Test;

class ComputerPlayerTest {
  private final ComputerPlayer computer = new ComputerPlayer();

  @Test
  void easyMoveIsLegal() {
    Board board = new Board();
    Move move = computer.choose(board, Disc.BLACK, Difficulty.EASY);
    assertNotNull(move);
    assertTrue(board.isLegal(move.row(), move.col(), Disc.BLACK));
  }

  @Test
  void mediumTakesAnOpenCorner() {
    Board board = Board.parse("""
        .WB.....
        ........
        ........
        ...WB...
        ...BW...
        ........
        ........
        ........
        """);
    Move move = computer.choose(board, Disc.BLACK, Difficulty.MEDIUM);
    assertEquals(new Move(0, 0), move);
  }

  @Test
  void twoEasyPlayersFinishTheGame() {
    Game game = new Game();
    int guard = 0;
    while (!game.isFinished() && guard++ < 80) {
      Move move = computer.choose(game.boardCopy(), game.turn(), Difficulty.EASY);
      assertNotNull(move);
      assertTrue(game.play(move.row(), move.col()));
    }
    assertTrue(game.isFinished());
    Board board = game.boardCopy();
    assertEquals(64, board.count(Disc.BLACK) + board.count(Disc.WHITE) + board.emptyCount());
    assertTrue(board.legalMoves(Disc.BLACK).isEmpty());
    assertTrue(board.legalMoves(Disc.WHITE).isEmpty());
  }
}
