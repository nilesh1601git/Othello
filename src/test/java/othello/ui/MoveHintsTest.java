package othello.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import othello.model.Disc;
import org.junit.jupiter.api.Test;

class MoveHintsTest {
  @Test
  void marksTheSideToMoveOnlyWhenVisibilityIsOn() {
    assertEquals(Disc.BLACK,
        OthelloFrame.hintsFor(true, false, false, true, Disc.BLACK, Disc.WHITE));
    assertEquals(Disc.WHITE,
        OthelloFrame.hintsFor(true, false, false, false, Disc.WHITE, Disc.WHITE));
  }

  @Test
  void hidesMarksWhenThePlayerTurnsVisibilityOff() {
    assertEquals(Disc.EMPTY,
        OthelloFrame.hintsFor(false, false, false, true, Disc.BLACK, Disc.BLACK));
  }

  @Test
  void hidesTheOpponentTurnAndFinishedOrThinkingBoards() {
    assertEquals(Disc.EMPTY,
        OthelloFrame.hintsFor(true, false, false, false, Disc.BLACK, Disc.WHITE));
    assertEquals(Disc.EMPTY,
        OthelloFrame.hintsFor(true, true, false, true, Disc.BLACK, Disc.BLACK));
    assertEquals(Disc.EMPTY,
        OthelloFrame.hintsFor(true, false, true, true, Disc.WHITE, Disc.BLACK));
  }
}
