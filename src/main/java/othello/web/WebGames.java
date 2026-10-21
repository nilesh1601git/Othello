package othello.web;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import othello.ai.ComputerPlayer;
import othello.ai.Difficulty;
import othello.model.Disc;
import othello.model.Game;
import othello.model.Move;
import othello.net.Protocol;

public final class WebGames {
  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final SecureRandom random = new SecureRandom();
  private final ComputerPlayer computer = new ComputerPlayer();
  private final Map<String, Room> rooms = new ConcurrentHashMap<>();

  public Access create(NewGame request) {
    Mode mode = Mode.parse(request.mode());
    String creatorName = cleanName(request.name(), "Player");
    Disc creatorColor = parseColor(request.color());
    Difficulty difficulty = parseDifficulty(request.difficulty());
    String id = nextCode();
    String token = nextToken();
    Room room = new Room(id, mode, difficulty, creatorColor, creatorName, token);
    rooms.put(id, room);
    List<GameView> frames = room.openingComputerMove(computer);
    return new Access(id, token, room.view(token), frames);
  }

  public Access join(String rawId, JoinGame request) {
    Room room = room(rawId);
    return room.join(cleanName(request.name(), "Guest"), nextToken());
  }

  public List<OpenGame> listOpen() {
    List<OpenGame> open = new ArrayList<>();
    for (Room room : rooms.values()) {
      room.appendIfOpen(open);
    }
    return List.copyOf(open);
  }

  public GameView state(String rawId, String token) {
    return room(rawId).view(requireToken(token));
  }

  public MoveResult move(String rawId, String token, MoveRequest request) {
    if (request == null || request.row() < 0 || request.row() > 7
        || request.col() < 0 || request.col() > 7) {
      throw new WebGameException(400, "Choose a square on the board.");
    }
    return room(rawId).move(requireToken(token), request.row(), request.col(), computer);
  }

  public MoveResult undo(String rawId, String token) {
    return room(rawId).undo(requireToken(token));
  }

  public GameView resign(String rawId, String token) {
    return room(rawId).resign(requireToken(token));
  }

  private Room room(String rawId) {
    String id = rawId == null ? "" : rawId.trim().toUpperCase(Locale.ROOT);
    Room room = rooms.get(id);
    if (room == null) {
      throw new WebGameException(404, "Game not found.");
    }
    return room;
  }

  private String nextCode() {
    String code;
    do {
      StringBuilder result = new StringBuilder(6);
      for (int index = 0; index < 6; index++) {
        result.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
      }
      code = result.toString();
    } while (rooms.containsKey(code));
    return code;
  }

  private String nextToken() {
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String requireToken(String token) {
    if (token == null || token.length() < 20 || token.length() > 100) {
      throw new WebGameException(401, "This player link is not valid.");
    }
    return token;
  }

  private static String cleanName(String name, String fallback) {
    String cleaned = Protocol.sanitizeName(name);
    return "Player".equals(cleaned) && (name == null || name.isBlank()) ? fallback : cleaned;
  }

  private static Disc parseColor(String color) {
    return "WHITE".equalsIgnoreCase(color) ? Disc.WHITE : Disc.BLACK;
  }

  private static Difficulty parseDifficulty(String value) {
    if (value == null) {
      return Difficulty.MEDIUM;
    }
    try {
      return Difficulty.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return Difficulty.MEDIUM;
    }
  }

  public record NewGame(String mode, String name, String color, String difficulty) {
  }

  public record JoinGame(String name) {
  }

  public record OpenGame(String gameId, String hostName, String hostColor, String origin) {
  }

  public record MoveRequest(int row, int col) {
  }

  public record Access(String gameId, String token, GameView state, List<GameView> frames) {
  }

  public record MoveResult(GameView state, List<GameView> frames) {
  }

  public record GameView(
      String gameId,
      String mode,
      String board,
      String turn,
      boolean finished,
      boolean waiting,
      int lastRow,
      int lastCol,
      boolean opponentPassed,
      String blackName,
      String whiteName,
      int blackCount,
      int whiteCount,
      String yourColor,
      boolean canMove,
      boolean canUndo,
      List<MoveView> legalMoves,
      String status) {
  }

  public record MoveView(int row, int col) {
  }

  public static final class WebGameException extends RuntimeException {
    private final int status;

    WebGameException(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }

  private enum Mode {
    COMPUTER,
    HOTSEAT,
    LAN;

    static Mode parse(String raw) {
      if (raw == null) {
        throw new WebGameException(400, "Choose a game mode.");
      }
      try {
        return valueOf(raw.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        throw new WebGameException(400, "Unknown game mode.");
      }
    }
  }

  private static final class Room {
    private final String id;
    private final Mode mode;
    private final Difficulty difficulty;
    private final Disc creatorColor;
    private final String creatorName;
    private final String creatorToken;
    private final Game game = new Game();
    private String guestName;
    private String guestToken;

    Room(String id, Mode mode, Difficulty difficulty, Disc creatorColor,
        String creatorName, String creatorToken) {
      this.id = id;
      this.mode = mode;
      this.difficulty = difficulty;
      this.creatorColor = mode == Mode.HOTSEAT ? Disc.BLACK : creatorColor;
      this.creatorName = creatorName;
      this.creatorToken = creatorToken;
    }

    synchronized List<GameView> openingComputerMove(ComputerPlayer computer) {
      if (mode != Mode.COMPUTER || creatorColor != Disc.WHITE) {
        return List.of();
      }
      playComputer(computer);
      return List.of(view(creatorToken));
    }

    synchronized void appendIfOpen(List<OpenGame> open) {
      if (mode == Mode.LAN && guestToken == null && !game.isFinished()) {
        open.add(new OpenGame(id, creatorName, creatorColor.name(), ""));
      }
    }

    synchronized Access join(String name, String token) {
      if (mode != Mode.LAN) {
        throw new WebGameException(409, "This is not a LAN game.");
      }
      if (guestToken != null) {
        throw new WebGameException(409, "This game already has two players.");
      }
      guestName = name;
      guestToken = token;
      return new Access(id, token, view(token), List.of());
    }

    synchronized MoveResult move(String token, int row, int col, ComputerPlayer computer) {
      Disc player = playerFor(token);
      if (isWaiting()) {
        throw new WebGameException(409, "Wait for another player to join.");
      }
      if (game.isFinished()) {
        throw new WebGameException(409, "This game is finished.");
      }
      if (mode != Mode.HOTSEAT && game.turn() != player) {
        throw new WebGameException(409, "Wait for your turn.");
      }
      if (!game.play(row, col)) {
        throw new WebGameException(400, "That move does not outflank a disc.");
      }
      List<GameView> frames = new ArrayList<>();
      frames.add(view(token));
      while (mode == Mode.COMPUTER && !game.isFinished() && game.turn() != creatorColor) {
        String before = game.encodeBoard();
        playComputer(computer);
        if (game.encodeBoard().equals(before)) {
          break;
        }
        frames.add(view(token));
      }
      return new MoveResult(view(token), List.copyOf(frames));
    }

    synchronized MoveResult undo(String token) {
      playerFor(token);
      if (mode == Mode.LAN) {
        throw new WebGameException(409, "Undo is unavailable in LAN games.");
      }
      if (!game.undo()) {
        throw new WebGameException(409, "There is no move to undo.");
      }
      List<GameView> frames = new ArrayList<>();
      frames.add(view(token));
      if (mode == Mode.COMPUTER && game.turn() != creatorColor && game.canUndo()) {
        game.undo();
        frames.add(view(token));
      }
      return new MoveResult(view(token), List.copyOf(frames));
    }

    synchronized GameView resign(String token) {
      Disc player = playerFor(token);
      if (!game.isFinished()) {
        String loser = nameFor(player);
        String winner = nameFor(player.opponent());
        game.finish(loser + " resigned. " + winner + " wins.");
      }
      return view(token);
    }

    synchronized GameView view(String token) {
      Disc player = playerFor(token);
      boolean canMove = !isWaiting() && !game.isFinished()
          && (mode == Mode.HOTSEAT || game.turn() == player);
      List<MoveView> legal = new ArrayList<>();
      if (canMove) {
        for (Move move : game.boardCopy().legalMoves(game.turn())) {
          legal.add(new MoveView(move.row(), move.col()));
        }
      }
      return new GameView(
          id,
          mode.name().toLowerCase(Locale.ROOT),
          game.encodeBoard(),
          game.turn().name(),
          game.isFinished(),
          isWaiting(),
          game.lastRow(),
          game.lastCol(),
          game.opponentPassed(),
          nameFor(Disc.BLACK),
          nameFor(Disc.WHITE),
          game.count(Disc.BLACK),
          game.count(Disc.WHITE),
          mode == Mode.HOTSEAT ? "BOTH" : player.name(),
          canMove,
          mode != Mode.LAN && game.canUndo(),
          List.copyOf(legal),
          status(player));
    }

    private void playComputer(ComputerPlayer computer) {
      Move move = computer.choose(game.boardCopy(), game.turn(), difficulty);
      if (move != null) {
        game.play(move.row(), move.col());
      }
    }

    private Disc playerFor(String token) {
      if (creatorToken.equals(token)) {
        return creatorColor;
      }
      if (guestToken != null && guestToken.equals(token)) {
        return creatorColor.opponent();
      }
      throw new WebGameException(401, "This player link is not valid.");
    }

    private String nameFor(Disc color) {
      if (mode == Mode.HOTSEAT) {
        return color == Disc.BLACK ? "Black" : "White";
      }
      if (mode == Mode.COMPUTER) {
        return color == creatorColor ? creatorName : "Computer";
      }
      if (color == creatorColor) {
        return creatorName;
      }
      return guestName == null ? "Waiting…" : guestName;
    }

    private boolean isWaiting() {
      return mode == Mode.LAN && guestToken == null;
    }

    private String status(Disc player) {
      if (isWaiting()) {
        return "Your game is on the Join list as " + id + ".";
      }
      if (game.endReason() != null) {
        return game.endReason();
      }
      if (game.isFinished()) {
        int black = game.count(Disc.BLACK);
        int white = game.count(Disc.WHITE);
        if (black == white) {
          return "Draw, " + black + " each.";
        }
        Disc winner = black > white ? Disc.BLACK : Disc.WHITE;
        return nameFor(winner) + " wins, " + Math.max(black, white)
            + " to " + Math.min(black, white) + ".";
      }
      String pass = game.opponentPassed() ? "The other player had no legal move. " : "";
      if (mode == Mode.HOTSEAT) {
        return pass + nameFor(game.turn()) + " to move.";
      }
      return pass + (game.turn() == player ? "Your turn." : "Waiting for " + nameFor(game.turn()) + ".");
    }
  }
}
