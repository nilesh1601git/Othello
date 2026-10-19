package othello.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class Addresses {
  private Addresses() {
  }

  public static List<String> localIpv4() {
    List<String> addresses = new ArrayList<>();
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface network = interfaces.nextElement();
        if (!network.isUp() || network.isLoopback()) {
          continue;
        }
        Enumeration<InetAddress> inetAddresses = network.getInetAddresses();
        while (inetAddresses.hasMoreElements()) {
          InetAddress address = inetAddresses.nextElement();
          if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
            addresses.add(address.getHostAddress());
          }
        }
      }
    } catch (SocketException ignored) {
      return List.of("127.0.0.1");
    }
    if (addresses.isEmpty()) {
      addresses.add("127.0.0.1");
    }
    return addresses;
  }

  public static List<InetAddress> broadcastAddresses() {
    List<InetAddress> broadcasts = new ArrayList<>();
    try {
      broadcasts.add(InetAddress.getByName("255.255.255.255"));
      broadcasts.add(InetAddress.getByName("127.0.0.1"));
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface network = interfaces.nextElement();
        if (!network.isUp() || network.isLoopback()) {
          continue;
        }
        network.getInterfaceAddresses().forEach(binding -> {
          InetAddress broadcast = binding.getBroadcast();
          if (broadcast != null && !broadcasts.contains(broadcast)) {
            broadcasts.add(broadcast);
          }
        });
      }
    } catch (Exception ignored) {
      return broadcasts;
    }
    return broadcasts;
  }
}
