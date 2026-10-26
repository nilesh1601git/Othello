package othello.web;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import othello.net.Addresses;
import othello.net.Discovery;
import othello.net.Protocol;
import othello.score.ScoreBook;
import othello.score.ScoreBook.ScoreBookException;
import othello.web.WebGames.Access;
import othello.web.WebGames.JoinGame;
import othello.web.WebGames.MoveRequest;
import othello.web.WebGames.NewGame;
import othello.web.WebGames.OpenGame;
import othello.web.WebGames.WebGameException;

public final class WebApp {
  private WebApp() {
  }

  public static Javalin start(int port) {
    ScoreBook scores = ScoreBook.open(ScoreBook.defaultPath());
    WebGames games = new WebGames(scores);
    Presence presence = new Presence(port);
    Javalin app = Javalin.create(config -> {
      config.showJavalinBanner = false;
      config.staticFiles.add(files -> {
        files.hostedPath = "/";
        files.directory = "/web";
        files.location = Location.CLASSPATH;
      });
      config.http.defaultContentType = "application/json";
    });

    app.get("/", context -> context.redirect("/index.html"));
    app.get("/api/users", context -> context.json(new UsersResponse(scores.users())));
    app.post("/api/users", context -> {
      ScoreBook.User created = scores.createUser(context.bodyAsClass(NewUser.class).name());
      context.status(HttpStatus.CREATED).json(created);
    });
    app.get("/api/scores", context ->
        context.json(new ScoresResponse(scores.history(context.queryParam("user")))));
    app.get("/api/games", context ->
        context.json(new OpenGames(games.listOpen())));
    app.get("/api/lan/scan", context ->
        context.json(new OpenGames(scan(games, port))));
    app.post("/api/games", context -> {
      Access created = games.create(context.bodyAsClass(NewGame.class), userId(context));
      presence.sync(games);
      context.status(HttpStatus.CREATED).json(created);
    });
    app.post("/api/games/{id}/join", context -> {
      Access joined = games.join(
          context.pathParam("id"), context.bodyAsClass(JoinGame.class), userId(context));
      presence.sync(games);
      context.json(joined);
    });
    app.get("/api/games/{id}", context ->
        context.json(games.state(context.pathParam("id"), token(context))));
    app.post("/api/games/{id}/move", context ->
        context.json(games.move(
            context.pathParam("id"), token(context), context.bodyAsClass(MoveRequest.class))));
    app.post("/api/games/{id}/undo", context ->
        context.json(games.undo(context.pathParam("id"), token(context))));
    app.post("/api/games/{id}/resign", context -> {
      context.json(games.resign(context.pathParam("id"), token(context)));
      presence.sync(games);
    });

    app.exception(WebGameException.class, (error, context) ->
        context.status(error.status()).json(new ErrorResponse(error.getMessage())));
    app.exception(ScoreBookException.class, (error, context) ->
        context.status(error.status()).json(new ErrorResponse(error.getMessage())));
    app.exception(Exception.class, (error, context) -> {
      if (context.status().getCode() < 400) {
        context.status(HttpStatus.BAD_REQUEST);
      }
      context.json(new ErrorResponse("The request could not be processed."));
    });

    app.events(events -> events.serverStopped(presence::close));
    app.start("0.0.0.0", port);
    System.out.println("Othello web interface:");
    System.out.println("  http://127.0.0.1:" + port + "/");
    for (String address : Addresses.localIpv4()) {
      System.out.println("  http://" + address + ":" + port + "/");
    }
    return app;
  }

  private static List<OpenGame> scan(WebGames games, int httpPort) {
    Map<String, OpenGame> unique = new LinkedHashMap<>();
    for (OpenGame game : games.listOpen()) {
      unique.put(game.gameId(), game);
    }
    for (OpenGame game : LanScan.remoteOpenGames(Protocol.DISCOVERY_PORT, 1600, httpPort)) {
      unique.putIfAbsent(game.gameId(), game);
    }
    return new ArrayList<>(unique.values());
  }

  private static String token(io.javalin.http.Context context) {
    String token = context.header("X-Player-Token");
    return token == null ? "" : token;
  }

  private static String userId(io.javalin.http.Context context) {
    String id = context.header("X-User-Id");
    return id == null ? "" : id.trim();
  }

  private record NewUser(String name) {
  }

  private record UsersResponse(List<ScoreBook.User> users) {
  }

  private record ScoresResponse(List<ScoreBook.Score> scores) {
  }

  private record OpenGames(List<OpenGame> games) {
  }

  private record ErrorResponse(String error) {
  }

  private static final class Presence implements AutoCloseable {
    private final int httpPort;
    private Discovery discovery;

    Presence(int httpPort) {
      this.httpPort = httpPort;
    }

    synchronized void sync(WebGames games) {
      List<OpenGame> open = games.listOpen();
      if (open.isEmpty()) {
        close();
        return;
      }
      if (discovery != null) {
        return;
      }
      Discovery next = new Discovery();
      try {
        next.announceWeb(httpPort, open.get(0).hostName());
        discovery = next;
      } catch (IOException ex) {
        next.close();
      }
    }

    @Override
    public synchronized void close() {
      if (discovery != null) {
        discovery.close();
        discovery = null;
      }
    }
  }
}
