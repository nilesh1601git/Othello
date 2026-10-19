package othello.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import othello.model.Disc;
import othello.model.Game;
import org.junit.jupiter.api.Test;

class BoardPanelTest {
  @Test
  void movesAnimateInOrderThenSettle() throws Exception {
    AtomicReference<BoardPanel> panel = new AtomicReference<>();
    Game game = new Game();
    SwingUtilities.invokeAndWait(() -> {
      panel.set(new BoardPanel((row, col) -> { }));
      panel.get().setSize(680, 700);
      panel.get().show(game.boardCopy(), -1, -1, Disc.BLACK, true);
    });

    game.play(2, 3);
    Game afterHuman = game.snapshot();
    game.play(game.boardCopy().legalMoves(Disc.WHITE).get(0).row(),
        game.boardCopy().legalMoves(Disc.WHITE).get(0).col());
    AtomicBoolean animatingAfterShow = new AtomicBoolean();
    SwingUtilities.invokeAndWait(() -> {
      panel.get().show(afterHuman.boardCopy(), 2, 3, Disc.EMPTY, false);
      panel.get().show(game.boardCopy(), game.lastRow(), game.lastCol(), Disc.BLACK, true);
      animatingAfterShow.set(panel.get().isAnimating());
    });
    assertTrue(animatingAfterShow.get());

    long deadline = System.currentTimeMillis() + 4_000;
    AtomicBoolean animating = new AtomicBoolean(true);
    while (animating.get() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
      SwingUtilities.invokeAndWait(() -> animating.set(panel.get().isAnimating()));
    }
    assertFalse(animating.get());
  }
}
