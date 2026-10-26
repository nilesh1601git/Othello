package othello.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import othello.score.ScoreBook.Score;
import othello.score.ScoreBook.ScoreBookException;
import othello.score.ScoreBook.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoreBookTest {
  @TempDir
  Path directory;

  @Test
  void usersAndScoresSurviveARestart() {
    Path file = directory.resolve("scores.txt");
    ScoreBook book = ScoreBook.open(file);
    User ada = book.createUser("Ada");
    Score score = book.remember(
        "GAME01", "computer", "Ada", "Computer", ada.id(), "", "", 34, 30, null);

    ScoreBook reloaded = ScoreBook.open(file);
    assertEquals("Ada", reloaded.user(ada.id()).orElseThrow().name());
    assertEquals(1, reloaded.history(ada.id()).size());
    assertEquals(score.summary(), reloaded.history(ada.id()).get(0).summary());
    assertEquals("Ada wins, 34 to 30.", reloaded.history(ada.id()).get(0).summary());
  }

  @Test
  void namesMustBeUniqueAndScoresCanBeForgotten() {
    ScoreBook book = ScoreBook.inMemory();
    book.createUser("Ada");
    ScoreBookException duplicate = assertThrows(ScoreBookException.class, () -> book.createUser(" ada "));
    assertEquals(409, duplicate.status());
    assertThrows(ScoreBookException.class, () -> book.createUser("  "));

    book.remember("GAME01", "hotseat", "Black", "White", "", "", "user", 40, 24, null);
    assertEquals(1, book.history("user").size());
    book.forget("GAME01");
    assertTrue(book.history("user").isEmpty());
    assertEquals(0, book.history(null).size());
  }

  @Test
  void resignationNamesTheWinnerEvenWhenTheyHaveFewerDiscs() {
    String summary = ScoreBook.summary("Ada resigned. Niels wins.", "Ada", "Niels", 40, 10);
    assertEquals("Ada resigned. Niels wins.", summary);
    assertEquals("WHITE", ScoreBook.outcome("Ada resigned. Niels wins.", "Ada", "Niels", 40, 10));
    assertTrue(!ScoreBook.isScored("Niels disconnected."));
  }
}
