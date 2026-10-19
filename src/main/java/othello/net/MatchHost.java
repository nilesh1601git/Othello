package othello.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import othello.model.Disc;
import othello.model.Game;

/**
 * Authoritative side of a LAN game. The host owns the board and sends a full
 * position after every accepted move.
 */
public final class MatchHost implements AutoCloseable {
  private final String hostName;
  private final Disc hostDisc;
  private final int requestedPort;
  private final Object lock = new Object();
  private final Game game = new Game();
  private MatchListener listener = new MatchListener() {
  };
  private volatile boolean running;
  private volatile boolean ready;
  private ServerSocket server;
  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private Discovery discovery;
  private Thread thread;
  private String guestName = "Guest";
  private int boundPort;

  public MatchHost(String hostName, Disc hostDisc, int port) {
    this.hostName = Protocol.sanitizeName(hostName);
    this.hostDisc = hostDisc == Disc.EMPTY ? Disc.BLACK : hostDisc;
    this.requestedPort = port;
  }

  public Disc hostDisc() {
    return hostDisc;
  }

  public int port() {
    return boundPort;
  }

  public void start(MatchListener matchListener) {
    this.listener = matchListener;
    running = true;
    thread = new Thread(this::run, "othello-host");
    thread.setDaemon(true);
    thread.start();
  }

  public boolean submitMove(int row, int col) {
    Game snapshot;
    synchronized (lock) {
      if (!ready || game.isFinished() || game.turn() != hostDisc || !game.play(row, col)) {
        return false;
      }
      if (!send(Protocol.state(game))) {
        return false;
      }
      snapshot = game.snapshot();
    }
    listener.onPosition(snapshot);
    return true;
  }

  public void resign() {
    endMatch(hostName + " resigned. " + guestName + " wins.");
  }

  @Override
  public void close() {
    synchronized (lock) {
      if (ready && !game.isFinished()) {
        send(Protocol.end(hostName + " left the game."));
      }
    }
    running = false;
    closeQuietly();
  }

  private void run() {
    try {
      server = new ServerSocket(requestedPort, 1);
      boundPort = server.getLocalPort();
      startDiscovery();
      listener.onWaiting(boundPort, Addresses.localIpv4());
      socket = server.accept();
      if (!running) {
        return;
      }
      socket.setTcpNoDelay(true);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
      if (!readHello()) {
        return;
      }
      Game snapshot;
      synchronized (lock) {
        send(Protocol.welcome(hostDisc.opponent(), hostName));
        send(Protocol.state(game));
        ready = true;
        snapshot = game.snapshot();
      }
      listener.onReady(hostDisc, guestName, snapshot);
      readLoop();
    } catch (BindException ex) {
      if (running) {
        listener.onClosed("Port " + requestedPort + " is already in use.");
      }
    } catch (IOException ex) {
      if (running) {
        listener.onClosed("Could not host the game.");
      }
    } finally {
      running = false;
      closeQuietly();
    }
  }

  private void startDiscovery() {
    discovery = new Discovery();
    try {
      discovery.announce(boundPort, hostName);
    } catch (IOException ex) {
      listener.onNotice("LAN discovery is off. The other player can still join with your IP address.");
    }
  }

  private boolean readHello() throws IOException {
    String line = in.readLine();
    if (!running) {
      return false;
    }
    if (line == null) {
      listener.onClosed("The other player disconnected before the game started.");
      return false;
    }
    if (Protocol.parse(line) instanceof Protocol.Hello hello) {
      guestName = hello.name();
      return true;
    }
    send(Protocol.end("Expected a player name."));
    listener.onClosed("The other program did not introduce itself.");
    return false;
  }

  private void readLoop() throws IOException {
    String line;
    while (running && (line = in.readLine()) != null) {
      Protocol.Message message = Protocol.parse(line);
      if (message instanceof Protocol.Move move) {
        applyGuestMove(move.row(), move.col());
      } else if (message instanceof Protocol.End end) {
        endMatch(end.reason());
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

  private void applyGuestMove(int row, int col) {
    Game snapshot = null;
    boolean ignored = false;
    synchronized (lock) {
      if (game.isFinished() || game.turn() == hostDisc || !game.play(row, col)) {
        send(Protocol.state(game));
        ignored = true;
      } else {
        send(Protocol.state(game));
        snapshot = game.snapshot();
      }
    }
    if (ignored) {
      listener.onNotice("Ignored a move that is not legal right now.");
      return;
    }
    listener.onPosition(snapshot);
  }

  private void endMatch(String reason) {
    Game snapshot;
    synchronized (lock) {
      if (!game.isFinished()) {
        game.finish(reason);
        send(Protocol.end(reason));
      }
      snapshot = game.snapshot();
    }
    listener.onPosition(snapshot);
  }

  private void peerLeft() {
    Game snapshot;
    boolean endedNow = false;
    synchronized (lock) {
      if (!game.isFinished()) {
        game.finish(guestName + " disconnected.");
        endedNow = true;
      }
      snapshot = game.snapshot();
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
    if (discovery != null) {
      discovery.close();
    }
    close(in);
    close(out);
    close(socket);
    close(server);
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
