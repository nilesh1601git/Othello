package othello.score;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import othello.net.Protocol;

/**
 * Players and finished-game scores. An optional file keeps them across restarts.
 * The newest score is first.
 */
public final class ScoreBook {
  private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int MAX_SCORES = 500;
  private final Path file;
  private final SecureRandom random = new SecureRandom();
  private final Object lock = new Object();
  private final List<User> users = new ArrayList<>();
  private final List<Score> scores = new ArrayList<>();

  private ScoreBook(Path file) {
    this.file = file;
  }

  public static ScoreBook inMemory() {
    return new ScoreBook(null);
  }

  public static Path defaultPath() {
    return Path.of(System.getProperty("user.home"), ".othello", "scores.txt");
  }

  public static ScoreBook open(Path file) {
    ScoreBook book = new ScoreBook(file);
    book.load();
    return book;
  }

  public User createUser(String rawName) {
    String visible = rawName == null ? "" : rawName.replaceAll("[\\r\\n|]", " ").trim();
    if (visible.isEmpty()) {
      throw new ScoreBookException(400, "Enter a name.");
    }
    String name = Protocol.sanitizeName(visible);
    synchronized (lock) {
      for (User user : users) {
        if (user.name().equalsIgnoreCase(name)) {
          throw new ScoreBookException(409, "That name is already in use.");
        }
      }
      User user = new User(nextUserId(), name);
      users.add(user);
      save();
      return user;
    }
  }

  public List<User> users() {
    synchronized (lock) {
      return List.copyOf(users);
    }
  }

  public Optional<User> user(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    synchronized (lock) {
      User found = findUser(id);
      return found == null ? Optional.empty() : Optional.of(found);
    }
  }

  public String newGameId() {
    synchronized (lock) {
      String id;
      do {
        id = "d" + randomCode(8);
      } while (hasGame(id));
      return id;
    }
  }

  public Score remember(
      String gameId,
      String mode,
      String blackName,
      String whiteName,
      String blackUserId,
      String whiteUserId,
      String recordedBy,
      int blackCount,
      int whiteCount,
      String endReason) {
    if (gameId == null || gameId.isBlank() || !isScored(endReason)) {
      return null;
    }
    synchronized (lock) {
      for (Score existing : scores) {
        if (existing.gameId().equals(gameId)) {
          return existing;
        }
      }
      Score score = new Score(
          randomCode(10),
          gameId,
          mode == null ? "" : mode,
          text(blackName),
          text(whiteName),
          idOrEmpty(blackUserId),
          idOrEmpty(whiteUserId),
          idOrEmpty(recordedBy),
          blackCount,
          whiteCount,
          outcome(endReason, blackName, whiteName, blackCount, whiteCount),
          System.currentTimeMillis(),
          summary(endReason, blackName, whiteName, blackCount, whiteCount));
      scores.add(0, score);
      while (scores.size() > MAX_SCORES) {
        scores.remove(scores.size() - 1);
      }
      save();
      return score;
    }
  }

  public void forget(String gameId) {
    if (gameId == null || gameId.isBlank()) {
      return;
    }
    synchronized (lock) {
      boolean removed = scores.removeIf(score -> score.gameId().equals(gameId));
      if (removed) {
        save();
      }
    }
  }

  public List<Score> history(String userId) {
    synchronized (lock) {
      if (userId == null || userId.isBlank()) {
        return List.copyOf(scores);
      }
      List<Score> matched = new ArrayList<>();
      for (Score score : scores) {
        if (userId.equals(score.blackUserId())
            || userId.equals(score.whiteUserId())
            || userId.equals(score.recordedBy())) {
          matched.add(score);
        }
      }
      return List.copyOf(matched);
    }
  }

  public static boolean isScored(String endReason) {
    if (endReason == null || endReason.isBlank()) {
      return true;
    }
    String lower = endReason.toLowerCase(Locale.ROOT);
    return !lower.contains("disconnected") && !lower.contains("left the game");
  }

  public static String outcome(
      String endReason, String blackName, String whiteName, int blackCount, int whiteCount) {
    String winner = winnerName(endReason);
    if (!winner.isEmpty() && winner.equals(blackName)) {
      return "BLACK";
    }
    if (!winner.isEmpty() && winner.equals(whiteName)) {
      return "WHITE";
    }
    if (blackCount == whiteCount) {
      return "DRAW";
    }
    return blackCount > whiteCount ? "BLACK" : "WHITE";
  }

  public static String summary(
      String endReason, String blackName, String whiteName, int blackCount, int whiteCount) {
    String winner = winnerName(endReason);
    if (!winner.isEmpty()) {
      String reason = endReason.trim();
      return reason.endsWith(".") ? reason : reason + ".";
    }
    String outcome = outcome(endReason, blackName, whiteName, blackCount, whiteCount);
    if ("DRAW".equals(outcome)) {
      return "Draw, " + blackCount + " each.";
    }
    String winnerName = "BLACK".equals(outcome) ? text(blackName) : text(whiteName);
    int winnerCount = "BLACK".equals(outcome) ? blackCount : whiteCount;
    int loserCount = "BLACK".equals(outcome) ? whiteCount : blackCount;
    return winnerName + " wins, " + winnerCount + " to " + loserCount + ".";
  }

  private static String winnerName(String endReason) {
    if (endReason == null) {
      return "";
    }
    String marker = " resigned. ";
    int at = endReason.indexOf(marker);
    if (at < 0) {
      return "";
    }
    String tail = endReason.substring(at + marker.length()).trim();
    if (tail.endsWith(".")) {
      tail = tail.substring(0, tail.length() - 1).trim();
    }
    if (!tail.endsWith(" wins")) {
      return "";
    }
    return tail.substring(0, tail.length() - " wins".length()).trim();
  }

  private boolean hasGame(String gameId) {
    for (Score score : scores) {
      if (score.gameId().equals(gameId)) {
        return true;
      }
    }
    return false;
  }

  private User findUser(String id) {
    for (User user : users) {
      if (user.id().equals(id)) {
        return user;
      }
    }
    return null;
  }

  private String nextUserId() {
    String id;
    do {
      id = randomCode(8);
    } while (findUser(id) != null);
    return id;
  }

  private String randomCode(int length) {
    StringBuilder result = new StringBuilder(length);
    for (int index = 0; index < length; index++) {
      result.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
    }
    return result.toString();
  }

  private void load() {
    if (file == null || !Files.isRegularFile(file)) {
      return;
    }
    try {
      for (String line : Files.readString(file).split("\\R")) {
        readLine(line.trim());
      }
    } catch (IOException ex) {
      System.err.println("Othello could not read score history: " + ex.getMessage());
    }
  }

  private void readLine(String line) {
    if (line.isEmpty() || line.startsWith("#")) {
      return;
    }
    try {
      if (line.startsWith("U|")) {
        String[] parts = line.split("\\|", 3);
        if (parts.length == 3 && !parts[1].isBlank() && !parts[2].isBlank()) {
          users.add(new User(parts[1], parts[2]));
        }
        return;
      }
      if (line.startsWith("R|")) {
        String[] parts = line.split("\\|", 14);
        if (parts.length == 14) {
          scores.add(new Score(
              parts[1],
              parts[2],
              parts[3],
              parts[4],
              parts[5],
              parts[6],
              parts[7],
              parts[8],
              Integer.parseInt(parts[9]),
              Integer.parseInt(parts[10]),
              parts[11],
              Long.parseLong(parts[12]),
              parts[13]));
        }
      }
    } catch (RuntimeException ignored) {
      // Skip a damaged line and keep the rest of the history.
    }
  }

  private void save() {
    if (file == null) {
      return;
    }
    try {
      Files.createDirectories(file.getParent());
      StringBuilder text = new StringBuilder("# othello-scores 1\n");
      for (User user : users) {
        text.append("U|").append(user.id()).append('|').append(user.name()).append('\n');
      }
      for (Score score : scores) {
        text.append("R|")
            .append(score.id()).append('|')
            .append(score.gameId()).append('|')
            .append(score.mode()).append('|')
            .append(score.blackName()).append('|')
            .append(score.whiteName()).append('|')
            .append(score.blackUserId()).append('|')
            .append(score.whiteUserId()).append('|')
            .append(score.recordedBy()).append('|')
            .append(score.blackCount()).append('|')
            .append(score.whiteCount()).append('|')
            .append(score.outcome()).append('|')
            .append(score.playedAt()).append('|')
            .append(score.summary()).append('\n');
      }
      Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
      Files.writeString(temporary, text.toString());
      try {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException ex) {
      System.err.println("Othello could not save score history: " + ex.getMessage());
    }
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? "Player" : value;
  }

  private static String idOrEmpty(String value) {
    return value == null ? "" : value;
  }

  public record User(String id, String name) {
  }

  public record Score(
      String id,
      String gameId,
      String mode,
      String blackName,
      String whiteName,
      String blackUserId,
      String whiteUserId,
      String recordedBy,
      int blackCount,
      int whiteCount,
      String outcome,
      long playedAt,
      String summary) {
  }

  public static final class ScoreBookException extends RuntimeException {
    private final int status;

    ScoreBookException(int status, String message) {
      super(message);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }
}
