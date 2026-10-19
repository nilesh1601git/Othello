package othello;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import othello.ui.OthelloFrame;
import othello.web.WebApp;

public final class App {
  private App() {
  }

  public static void main(String[] args) {
    if (hasArgument(args, "--web") || GraphicsEnvironment.isHeadless()) {
      WebApp.start(webPort(args));
      return;
    }
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
      } catch (Exception ignored) {
        // The cross-platform look is a preference. The game still runs with the default.
      }
      new OthelloFrame().setVisible(true);
    });
  }

  private static boolean hasArgument(String[] args, String expected) {
    for (String argument : args) {
      if (expected.equals(argument)) {
        return true;
      }
    }
    return false;
  }

  private static int webPort(String[] args) {
    for (int index = 0; index < args.length - 1; index++) {
      if ("--port".equals(args[index])) {
        try {
          int port = Integer.parseInt(args[index + 1]);
          if (port >= 1 && port <= 65535) {
            return port;
          }
        } catch (NumberFormatException ignored) {
          // Use the safe default below.
        }
      }
    }
    return 8080;
  }
}
