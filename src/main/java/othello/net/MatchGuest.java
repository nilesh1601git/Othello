package othello.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import othello.model.Disc;
import othello.model.Game;

/** Joins a host and treats every STATE message as the new position. */
public final class MatchGuest implements AutoCloseable {
  private final Object lock = new Object();
  private MatchListener listener = new MatchListener() {
  };
  private volatile boolean running;
  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private Thread thread;
  private Disc myColor = Disc.WHITE;
  private Game latest = new Game();
  private String myName = "Guest";
  private String opponentName = "Host";
  private boolean welcomed;
  private boolean announced;

  public void connect(String host, int port, String name, MatchListener matchListener) {
    this.listener = matchListener;
    this.myName = Protocol.sanitizeName(name);
    running = true;
    thread = new Thread(() -> run(host, port), "othello-guest");
    thread.setDaemon(true);
    thread.start();
  }

  public boolean submitMove(int row, int col) {
    synchronized (lock) {
      if (!announced || latest.isFinished() || latest.turn() != myColor) {
        return false;
      }
      return send(Protocol.move(row, col));
    }
  }

  public void resign() {
    endLocal(myName + " resigned. " + opponentName + " wins.", true);
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (announced && !latest.isFinished()) {
        send(Protocol.end(myName + " left the game."));
      }
    }
    running = false;
    closeQuietly();
  }

  private void run(String host, int port) {
    try {
      socket = new Socket();
      socket.connect(new InetSocketAddress(host, port), 5_000);
      socket.setTcpNoDelay(true);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
      if (!send(Protocol.hello(myName))) {
        listener.onClosed("Could not reach " + host + ":" + port + ".");
        return;
      }
      readLoop();
    } catch (IOException ex) {
      if (running) {
        listener.onClosed("Could not connect to " + host + ":" + port + ".");
      }
    } finally {
      running = false;
      closeQuietly();
    }
  }

  private void readLoop() throws IOException {
    String line;
    while (running && (line = in.readLine()) != null) {
      Protocol.Message message = Protocol.parse(line);
      if (message instanceof Protocol.Welcome welcome) {
        myColor = welcome.you();
        opponentName = welcome.opponentName();
        welcomed = true;
      } else if (message instanceof Protocol.State state) {
        if (!welcomed) {
          listener.onClosed("The host sent a board before the welcome.");
          return;
        }
        publish(state.game(), !announced);
      } else if (message instanceof Protocol.End end) {
        endLocal(end.reason(), false);
        return;
      } else if (message instanceof Protocol.Bye) {
        peerLeft();
        return;
      }
    }
    if (running) {
      peerLeft();
    }
  }

  private void publish(Game position, boolean first) {
    synchronized (lock) {
      latest = position;
      if (first) {
        announced = true;
      }
    }
    if (first) {
      listener.onReady(myColor, opponentName, position);
      return;
    }
    listener.onPosition(position);
  }

  private void endLocal(String reason, boolean tellHost) {
    Game snapshot;
    synchronized (lock) {
      if (latest.isFinished()) {
        return;
      }
      snapshot = latest.snapshot();
      snapshot.finish(reason);
      latest = snapshot;
      if (tellHost) {
        send(Protocol.end(reason));
      }
    }
    listener.onPosition(snapshot);
  }

  private void peerLeft() {
    if (!announced) {
      listener.onClosed(opponentName + " disconnected before the game started.");
      return;
    }
    Game snapshot;
    boolean endedNow = false;
    synchronized (lock) {
      if (!latest.isFinished()) {
        snapshot = latest.snapshot();
        snapshot.finish(opponentName + " disconnected.");
        latest = snapshot;
        endedNow = true;
      } else {
        snapshot = latest;
      }
    }
    if (endedNow) {
      listener.onPosition(snapshot);
      listener.onClosed(snapshot.endReason());
    }
  }

  private boolean send(String line) {
    PrintWriter writer = out;
    if (writer == null) {
      return false;
    }
    writer.println(line);
    return !writer.checkError();
  }

  private void closeQuietly() {
    close(in);
    close(out);
    close(socket);
  }

  private static void close(AutoCloseable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (Exception ignored) {
      // The match is already going away.
    }
  }
}
