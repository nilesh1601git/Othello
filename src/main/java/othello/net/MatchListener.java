package othello.net;

import java.util.List;
import othello.model.Disc;
import othello.model.Game;

public interface MatchListener {
  default void onWaiting(int port, List<String> addresses) {
  }

  default void onReady(Disc yourColor, String opponentName, Game position) {
  }

  default void onPosition(Game position) {
  }

  default void onNotice(String message) {
  }

  default void onClosed(String reason) {
  }
}
