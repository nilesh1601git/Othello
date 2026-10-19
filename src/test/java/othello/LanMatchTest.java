package othello;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import othello.model.Disc;
import othello.model.Game;
import othello.model.Move;
import othello.net.Discovery;
import othello.net.MatchGuest;
import othello.net.MatchHost;
import othello.net.MatchListener;
import othello.net.Protocol;
import org.junit.jupiter.api.Test;

class LanMatchTest {
  @Test
  void stateMessageRoundTrips() {
    Game game = new Game();
    assertTrue(game.play(2, 3));
    Protocol.Message parsed = Protocol.parse(Protocol.state(game));
    Protocol.State state = assertInstanceOf(Protocol.State.class, parsed);
    Game copy = state.game();
    assertEquals(game.encodeBoard(), copy.encodeBoard());
    assertEquals(game.turn(), copy.turn());
    assertEquals(2, copy.lastRow());
    assertEquals(3, copy.lastCol());
  }

  @Test
  void discoveryFindsAHostOnLocalhost() throws Exception {
    int udpPort;
    try (DatagramSocket free = new DatagramSocket(0)) {
      udpPort = free.getLocalPort();
    }
    CountDownLatch found = new CountDownLatch(1);
    AtomicReference<Discovery.Advertisement> advertisement = new AtomicReference<>();
    Discovery host = new Discovery(udpPort);
    Discovery guest = new Discovery(udpPort);
    try {
      host.announce(48111, "Table");
      guest.listen(seen -> {
        if ("Table".equals(seen.name()) && seen.port() == 48111) {
          advertisement.set(seen);
          found.countDown();
        }
      });
      assertTrue(found.await(5, TimeUnit.SECONDS), "guest did not see the host");
      assertEquals(48111, advertisement.get().port());
    } finally {
      guest.close();
      host.close();
    }
  }

  @Test
  void hostAndGuestPlayAMoveEach() throws Exception {
    CountDownLatch waiting = new CountDownLatch(1);
    CountDownLatch hostReady = new CountDownLatch(1);
    CountDownLatch guestReady = new CountDownLatch(1);
    CountDownLatch guestSawHostMove = new CountDownLatch(1);
    AtomicInteger port = new AtomicInteger();
    AtomicReference<Game> guestGame = new AtomicReference<>();
    AtomicReference<Game> hostGame = new AtomicReference<>();

    MatchHost host = new MatchHost("Ada", Disc.BLACK, 0);
    MatchGuest guest = new MatchGuest();
    try {
      host.start(new MatchListener() {
        @Override
        public void onWaiting(int boundPort, java.util.List<String> addresses) {
          port.set(boundPort);
          waiting.countDown();
        }

        @Override
        public void onReady(Disc yourColor, String opponentName, Game position) {
          hostGame.set(position);
          hostReady.countDown();
        }

        @Override
        public void onPosition(Game position) {
          hostGame.set(position);
        }
      });
      assertTrue(waiting.await(3, TimeUnit.SECONDS));
      guest.connect("127.0.0.1", port.get(), "Niels", new MatchListener() {
        @Override
        public void onReady(Disc yourColor, String opponentName, Game position) {
          assertEquals(Disc.WHITE, yourColor);
          assertEquals("Ada", opponentName);
          guestGame.set(position);
          guestReady.countDown();
        }

        @Override
        public void onPosition(Game position) {
          guestGame.set(position);
          if (position.lastRow() == 2 && position.lastCol() == 3) {
            guestSawHostMove.countDown();
          }
        }
      });
      assertTrue(hostReady.await(3, TimeUnit.SECONDS));
      assertTrue(guestReady.await(3, TimeUnit.SECONDS));
      assertTrue(host.submitMove(2, 3));
      assertTrue(guestSawHostMove.await(3, TimeUnit.SECONDS));
      assertEquals(Disc.BLACK, guestGame.get().boardCopy().get(2, 3));

      Move reply = guestGame.get().boardCopy().legalMoves(Disc.WHITE).get(0);
      assertTrue(guest.submitMove(reply.row(), reply.col()));
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      Game afterReply = hostGame.get();
      while (System.nanoTime() < deadline
          && (afterReply == null
          || afterReply.lastRow() != reply.row()
          || afterReply.lastCol() != reply.col())) {
        Thread.sleep(30);
        afterReply = hostGame.get();
      }
      assertNotNull(afterReply);
      assertEquals(reply.row(), afterReply.lastRow());
      assertEquals(reply.col(), afterReply.lastCol());
      assertEquals(Disc.WHITE, afterReply.boardCopy().get(reply.row(), reply.col()));
    } finally {
      guest.close();
      host.close();
    }
  }
}
