# Othello

A Java 17 Othello game with desktop and web interfaces. Play the computer, pass one device between two people, or play a friend on another device on the same network.

## Desktop interface

```bash
cd Othello
mvn -q package
java -jar target/othello-0.1.0.jar
```

## Web interface

```bash
java -jar target/othello-0.1.0.jar --web
```

Open `http://localhost:8080/`. To use another port:

```bash
java -jar target/othello-0.1.0.jar --web --port 9090
```

The server prints LAN addresses when it starts. Open one of those addresses on
the other device. For a LAN match, the host creates a game. The other player
chooses **Join a LAN game**, then **Scan network**, and picks the hosted game.
The six-character code and **Share game** link still work as a fallback. Both
browsers must connect to the same running Othello web server, or to a server
that can see the host on the LAN. Allow the selected HTTP port through the host
firewall.

In a headless environment, the application starts the web interface automatically.

## Modes

- **Play vs Computer.** Choose Easy, Medium, or Hard, and choose Black or White. Easy plays a random legal move. Medium and Hard search ahead, with Hard looking further and using a little more time.
- **Two players, this device.** Black and White take turns at the same screen. Undo takes back the last disc.
- **LAN.** One person chooses **Host a LAN game**. The other, on a device on the same network, chooses **Join a LAN game** and **Scan network**. The host's game should appear in the list. If it does not, type the address or game code shown on the host screen.

**Show valid moves**, on the game screen, marks every square the player to move can play. Turn it off to hide the marks. Those squares stay playable either way. The choice is remembered.

The desktop LAN game listens on TCP port **47820** unless you change it. Finding
desktop games uses UDP port **47821**. The web interface uses HTTP port **8080**
by default. The server's board is authoritative in both LAN modes.

## Rules

Black starts. A move must trap one or more opposing discs in a straight line between the new disc and one of your discs. Those trapped discs flip. If you cannot move, you pass. When neither player can move, the higher disc count wins.

Rows are numbered 1 to 8 from the top. Columns are a to h from the left.
