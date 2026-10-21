package othello.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import othello.net.Addresses;
import othello.net.Discovery;
import othello.web.WebGames.OpenGame;

/**
 * Finds waiting LAN games on other Othello web servers heard on the local
 * network. Only private IPv4 HTTP endpoints are contacted.
 */
public final class LanScan {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofMillis(400))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

  private LanScan() {
  }

  public static List<OpenGame> remoteOpenGames(int discoveryPort, int durationMs, int localHttpPort) {
    Set<String> self = selfKeys(localHttpPort);
    List<OpenGame> games = new ArrayList<>();
    try {
      for (Discovery.Advertisement found : Discovery.collect(discoveryPort, durationMs)) {
        if (!found.web() || self.contains(found.address() + ":" + found.port())) {
          continue;
        }
        if (!isSafeLanAddress(found.address())) {
          continue;
        }
        String origin = "http://" + found.address() + ":" + found.port();
        games.addAll(fetchOpen(origin));
      }
    } catch (Exception ignored) {
      return games;
    }
    return games;
  }

  static boolean isSafeLanAddress(String address) {
    if (address == null || address.isBlank()) {
      return false;
    }
    try {
      InetAddress ip = InetAddress.getByName(address);
      if (!(ip instanceof Inet4Address) || ip.isAnyLocalAddress() || ip.isMulticastAddress()) {
        return false;
      }
      return ip.isSiteLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress();
    } catch (Exception ex) {
      return false;
    }
  }

  private static Set<String> selfKeys(int localHttpPort) {
    Set<String> keys = new HashSet<>();
    keys.add("127.0.0.1:" + localHttpPort);
    for (String address : Addresses.localIpv4()) {
      keys.add(address + ":" + localHttpPort);
    }
    return keys;
  }

  private static List<OpenGame> fetchOpen(String origin) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(origin + "/api/games"))
          .timeout(Duration.ofMillis(600))
          .header("Accept", "application/json")
          .GET()
          .build();
      HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        return List.of();
      }
      JsonNode list = JSON.readTree(response.body()).get("games");
      if (list == null || !list.isArray()) {
        return List.of();
      }
      List<OpenGame> games = new ArrayList<>();
      for (JsonNode node : list) {
        String id = text(node, "gameId");
        if (id.isEmpty()) {
          continue;
        }
        String hostName = text(node, "hostName");
        String hostColor = text(node, "hostColor");
        games.add(new OpenGame(
            id,
            hostName.isEmpty() ? "Host" : hostName,
            hostColor.isEmpty() ? "BLACK" : hostColor,
            origin));
      }
      return games;
    } catch (Exception ex) {
      return List.of();
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText("").trim();
  }
}
