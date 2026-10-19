package othello.web;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;
import othello.net.Addresses;
import othello.web.WebGames.JoinGame;
import othello.web.WebGames.MoveRequest;
import othello.web.WebGames.NewGame;
import othello.web.WebGames.WebGameException;

public final class WebApp {
  private WebApp() {
  }

  public static Javalin start(int port) {
    WebGames games = new WebGames();
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
    app.post("/api/games", context ->
        context.status(HttpStatus.CREATED).json(games.create(context.bodyAsClass(NewGame.class))));
    app.post("/api/games/{id}/join", context ->
        context.json(games.join(context.pathParam("id"), context.bodyAsClass(JoinGame.class))));
    app.get("/api/games/{id}", context ->
        context.json(games.state(context.pathParam("id"), token(context))));
    app.post("/api/games/{id}/move", context ->
        context.json(games.move(
            context.pathParam("id"), token(context), context.bodyAsClass(MoveRequest.class))));
    app.post("/api/games/{id}/undo", context ->
        context.json(games.undo(context.pathParam("id"), token(context))));
    app.post("/api/games/{id}/resign", context ->
        context.json(games.resign(context.pathParam("id"), token(context))));

    app.exception(WebGameException.class, (error, context) ->
        context.status(error.status()).json(new ErrorResponse(error.getMessage())));
    app.exception(Exception.class, (error, context) -> {
      if (context.status().getCode() < 400) {
        context.status(HttpStatus.BAD_REQUEST);
      }
      context.json(new ErrorResponse("The request could not be processed."));
    });

    app.start("0.0.0.0", port);
    System.out.println("Othello web interface:");
    System.out.println("  http://127.0.0.1:" + port + "/");
    for (String address : Addresses.localIpv4()) {
      System.out.println("  http://" + address + ":" + port + "/");
    }
    return app;
  }

  private static String token(io.javalin.http.Context context) {
    String token = context.header("X-Player-Token");
    return token == null ? "" : token;
  }

  private record ErrorResponse(String error) {
  }
}
