package othello.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * UDP presence for LAN games. The host answers probes on the discovery port.
 * A guest probes the LAN and collects replies. Players can still type an IP
 * when discovery cannot bind.
 */
public final class Discovery implements AutoCloseable {
  private final int udpPort;
  private volatile boolean running;
  private DatagramSocket socket;
  private int tcpPort;
  private String name = "Player";
  private Thread loop;
  private Consumer<Advertisement> onFound = advertisement -> {
  };

  public Discovery() {
    this(Protocol.DISCOVERY_PORT);
  }

  public Discovery(int udpPort) {
    this.udpPort = udpPort;
  }

  public void announce(int tcpPort, String hostName) throws IOException {
    this.tcpPort = tcpPort;
    this.name = Protocol.sanitizeName(hostName);
    socket = new DatagramSocket(null);
    socket.setReuseAddress(true);
    socket.setBroadcast(true);
    socket.bind(new InetSocketAddress(udpPort));
    socket.setSoTimeout(400);
    running = true;
    loop = daemon("othello-announce", this::announceLoop);
    loop.start();
  }

  public void listen(Consumer<Advertisement> found) throws IOException {
    this.onFound = found;
    socket = openListener();
    socket.setSoTimeout(400);
    running = true;
    loop = daemon("othello-listen", this::listenLoop);
    loop.start();
  }

  public static Advertisement parseAnnounce(String message, String sourceAddress) {
    if (message == null || sourceAddress == null) {
      return null;
    }
    String[] parts = message.split("\\|", 4);
    if (parts.length != 4 || !"OTHELLO".equals(parts[0]) || !"ANNOUNCE".equals(parts[1])) {
      return null;
    }
    try {
      int port = Integer.parseInt(parts[2]);
      if (port < 1 || port > 65535) {
        return null;
      }
      return new Advertisement(sourceAddress, port, Protocol.sanitizeName(parts[3]));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  @Override
  public void close() {
    running = false;
    DatagramSocket open = socket;
    if (open != null) {
      open.close();
    }
    Thread current = loop;
    if (current != null) {
      current.interrupt();
    }
  }

  private void announceLoop() {
    while (running) {
      send(announceText(), udpPort);
      if (!receiveOnce(true)) {
        return;
      }
    }
  }

  private void listenLoop() {
    while (running) {
      send("OTHELLO|DISCOVER", udpPort);
      if (!receiveOnce(false)) {
        return;
      }
    }
  }

  private boolean receiveOnce(boolean replyToDiscover) {
    byte[] buffer = new byte[512];
    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
    long until = System.currentTimeMillis() + 1_000;
    while (running && System.currentTimeMillis() < until) {
      try {
        socket.receive(packet);
      } catch (SocketTimeoutException ex) {
        continue;
      } catch (IOException ex) {
        return false;
      }
      String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
      if (replyToDiscover && text.startsWith("OTHELLO|DISCOVER")) {
        reply(packet);
        continue;
      }
      if (replyToDiscover) {
        continue;
      }
      Advertisement found = parseAnnounce(text, packet.getAddress().getHostAddress());
      if (found != null) {
        onFound.accept(found);
      }
    }
    return true;
  }

  private void reply(DatagramPacket probe) {
    byte[] payload = announceText().getBytes(StandardCharsets.UTF_8);
    try {
      socket.send(new DatagramPacket(payload, payload.length, probe.getAddress(), probe.getPort()));
    } catch (IOException ignored) {
      running = false;
    }
  }

  private void send(String text, int port) {
    if (!running || socket == null || socket.isClosed()) {
      return;
    }
    byte[] payload = text.getBytes(StandardCharsets.UTF_8);
    for (InetAddress target : Addresses.broadcastAddresses()) {
      try {
        socket.send(new DatagramPacket(payload, payload.length, target, port));
      } catch (IOException ignored) {
        // Try the next interface. One blocked broadcast should not stop the rest.
      }
    }
  }

  private DatagramSocket openListener() throws SocketException {
    try {
      DatagramSocket bound = new DatagramSocket(null);
      bound.setReuseAddress(true);
      bound.setBroadcast(true);
      bound.bind(new InetSocketAddress(udpPort));
      return bound;
    } catch (SocketException busy) {
      DatagramSocket ephemeral = new DatagramSocket();
      ephemeral.setBroadcast(true);
      return ephemeral;
    }
  }

  private String announceText() {
    return "OTHELLO|ANNOUNCE|" + tcpPort + "|" + name;
  }

  private static Thread daemon(String name, Runnable task) {
    Thread thread = new Thread(task, name);
    thread.setDaemon(true);
    return thread;
  }

  public record Advertisement(String address, int port, String name) {
    public String label() {
      return name + "   " + address + ":" + port;
    }
  }
}
