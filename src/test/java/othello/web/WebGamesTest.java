package othello.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import othello.web.WebGames.Access;
import othello.web.WebGames.JoinGame;
import othello.web.WebGames.MoveRequest;
import othello.web.WebGames.NewGame;
import othello.web.WebGames.WebGameException;
import org.junit.jupiter.api.Test;

class WebGamesTest {
  @Test
  void computerMovesAfterTheHuman() {
    WebGames games = new WebGames();
    Access access = games.create(new NewGame("computer", "Ada", "BLACK", "EASY"));
    WebGames.GameView opening = access.state();
    WebGames.MoveView move = opening.legalMoves().get(0);

    WebGames.MoveResult after = games.move(
        access.gameId(), access.token(), new MoveRequest(move.row(), move.col()));

    assertEquals(2, after.frames().size());
    assertEquals(5, after.frames().get(0).blackCount() + after.frames().get(0).whiteCount());
    assertEquals("BLACK", after.state().turn());
    assertEquals("BLACK", after.state().yourColor());
    assertEquals(6, after.state().blackCount() + after.state().whiteCount());
    assertTrue(after.state().canMove());
  }

  @Test
  void lanPlayersReceiveDifferentPrivateTokens() {
    WebGames games = new WebGames();
    Access host = games.create(new NewGame("lan", "Host", "WHITE", "EASY"));
    assertTrue(host.state().waiting());
    assertFalse(host.state().canMove());

    Access guest = games.join(host.gameId(), new JoinGame("Guest"));
    assertNotEquals(host.token(), guest.token());
    assertFalse(guest.state().waiting());
    assertEquals("BLACK", guest.state().yourColor());
    assertTrue(guest.state().canMove());
    assertFalse(games.state(host.gameId(), host.token()).canMove());
  }

  @Test
  void openLanGamesAppearUntilAGuestJoins() {
    WebGames games = new WebGames();
    Access host = games.create(new NewGame("lan", "Ada", "WHITE", "EASY"));
    games.create(new NewGame("computer", "Bot", "BLACK", "EASY"));
    games.create(new NewGame("hotseat", "Table", "BLACK", "EASY"));

    List<WebGames.OpenGame> open = games.listOpen();
    assertEquals(1, open.size());
    assertEquals(host.gameId(), open.get(0).gameId());
    assertEquals("Ada", open.get(0).hostName());
    assertEquals("WHITE", open.get(0).hostColor());

    games.join(host.gameId(), new JoinGame("Niels"));
    assertTrue(games.listOpen().isEmpty());
  }

  @Test
  void aPlayerCannotUseAnotherToken() {
    WebGames games = new WebGames();
    Access game = games.create(new NewGame("hotseat", "Players", "BLACK", "EASY"));
    WebGameException error = assertThrows(
        WebGameException.class,
        () -> games.state(game.gameId(), "abcdefghijklmnopqrstuvwxyz"));
    assertEquals(401, error.status());
  }
}
