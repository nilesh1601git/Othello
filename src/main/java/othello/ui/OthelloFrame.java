package othello.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import othello.ai.ComputerPlayer;
import othello.ai.Difficulty;
import othello.model.Disc;
import othello.model.Game;
import othello.model.Move;
import othello.net.Discovery;
import othello.net.MatchGuest;
import othello.net.MatchHost;
import othello.net.MatchListener;
import othello.net.Protocol;

public final class OthelloFrame extends JFrame {
  private final CardLayout cards = new CardLayout();
  private final JPanel root = new JPanel(cards);
  private final BoardPanel boardPanel = new BoardPanel(this::onCell);
  private final ComputerPlayer computer = new ComputerPlayer();
  private final JLabel status = new JLabel("Black to move", SwingConstants.CENTER);
  private final JLabel meta = new JLabel(" ", SwingConstants.CENTER);
  private final JLabel blackNameLabel = new JLabel("Black");
  private final JLabel whiteNameLabel = new JLabel("White");
  private final JLabel blackCountLabel = new JLabel("2");
  private final JLabel whiteCountLabel = new JLabel("2");
  private final JPanel blackCard = scoreCard();
  private final JPanel whiteCard = scoreCard();
  private final JButton undoButton = Widgets.button("Undo");
  private final JButton resignButton = Widgets.button("Resign");
  private final JButton newGameButton = Widgets.button("New game");
  private final DefaultListModel<Discovery.Advertisement> foundGames = new DefaultListModel<>();

  private JComboBox<Difficulty> difficultyBox;
  private JComboBox<String> colorBox;
  private JTextField hostNameField;
  private JTextField hostPortField;
  private JRadioButton hostPlaysBlack;
  private JTextArea hostAddresses;
  private JLabel hostWaitStatus;
  private JPanel hostForm;
  private JPanel hostWait;
  private JPanel hostDeck;
  private JTextField joinNameField;
  private JTextField joinAddressField;
  private JTextField joinPortField;
  private JList<Discovery.Advertisement> joinList;

  private Game game = new Game();
  private Mode mode = Mode.HOTSEAT;
  private Disc humanDisc = Disc.BLACK;
  private Difficulty difficulty = Difficulty.MEDIUM;
  private String blackName = "Black";
  private String whiteName = "White";
  private String localName = "You";
  private boolean thinking;
  private int session;
  private boolean disposed;
  private MatchHost host;
  private MatchGuest guest;
  private Discovery browser;

  public OthelloFrame() {
    super("Othello");
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    setMinimumSize(new Dimension(880, 720));
    setSize(980, 780);
    setLocationRelativeTo(null);
    setIconImage(icon());
    Widgets.dark(root);
    root.add(menuPanel(), "menu");
    root.add(hostPanel(), "host");
    root.add(joinPanel(), "join");
    root.add(gamePanel(), "game");
    setContentPane(root);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        disposed = true;
        session++;
        shutdownNet();
        dispose();
      }
    });
    showCard("menu");
  }

  private JPanel menuPanel() {
    difficultyBox = new JComboBox<>(Difficulty.values());
    difficultyBox.setSelectedItem(Difficulty.MEDIUM);
    styleCombo(difficultyBox);
    colorBox = new JComboBox<>(new String[] {"Black", "White"});
    styleCombo(colorBox);

    JPanel panel = column();
    panel.add(Box.createVerticalStrut(36));
    panel.add(Widgets.title("OTHELLO"));
    panel.add(Box.createVerticalStrut(8));
    panel.add(Widgets.muted("Outflank a line of discs, then flip them."));
    panel.add(Box.createVerticalStrut(28));
    panel.add(menuButton("Play vs Computer", this::startComputer));
    panel.add(Box.createVerticalStrut(10));
    panel.add(optionRow("Difficulty", difficultyBox, "You play", colorBox));
    panel.add(Box.createVerticalStrut(22));
    panel.add(menuButton("Two players, this device", this::startHotseat));
    panel.add(Box.createVerticalStrut(22));
    panel.add(menuButton("Host a LAN game", () -> {
      showHostForm();
      showCard("host");
    }));
    panel.add(Box.createVerticalStrut(12));
    panel.add(menuButton("Join a LAN game", () -> {
      startBrowsing();
      showCard("join");
    }));
    panel.add(Box.createVerticalStrut(22));
    panel.add(menuButton("How to play", this::showRules));
    panel.add(Box.createVerticalGlue());
    return panel;
  }

  private JPanel hostPanel() {
    hostNameField = Widgets.field("Host", 16);
    hostPortField = Widgets.field(Integer.toString(Protocol.DEFAULT_PORT), 6);
    hostPlaysBlack = new JRadioButton("Black (moves first)", true);
    JRadioButton white = new JRadioButton("White");
    styleRadio(hostPlaysBlack);
    styleRadio(white);
    ButtonGroup colors = new ButtonGroup();
    colors.add(hostPlaysBlack);
    colors.add(white);

    hostForm = column();
    hostForm.add(Box.createVerticalStrut(28));
    hostForm.add(Widgets.title("Host"));
    hostForm.add(Box.createVerticalStrut(8));
    hostForm.add(Widgets.muted("The other player joins from a device on this network."));
    hostForm.add(Box.createVerticalStrut(18));
    hostForm.add(labeled("Your name", hostNameField));
    hostForm.add(Box.createVerticalStrut(10));
    hostForm.add(labeled("Port", hostPortField));
    hostForm.add(Box.createVerticalStrut(10));
    JPanel colorsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
    Widgets.dark(colorsRow);
    colorsRow.add(hostPlaysBlack);
    colorsRow.add(white);
    hostForm.add(colorsRow);
    hostForm.add(Box.createVerticalStrut(16));
    hostForm.add(menuButton("Start hosting", this::startHosting));
    hostForm.add(Box.createVerticalStrut(10));
    hostForm.add(menuButton("Back", this::backToMenu));

    hostAddresses = Widgets.readOnly("Waiting…");
    hostAddresses.setRows(4);
    hostWaitStatus = Widgets.muted("Waiting for the other player…");
    hostWait = column();
    hostWait.add(Box.createVerticalStrut(28));
    hostWait.add(Widgets.title("Waiting"));
    hostWait.add(Box.createVerticalStrut(8));
    hostWait.add(hostWaitStatus);
    hostWait.add(Box.createVerticalStrut(12));
    hostWait.add(Widgets.muted("On the other device, choose Join and pick this game,"));
    hostWait.add(Widgets.muted("or enter one of these addresses."));
    hostWait.add(Box.createVerticalStrut(12));
    JScrollPane scroll = new JScrollPane(hostAddresses);
    scroll.setPreferredSize(new Dimension(360, 110));
    scroll.setBorder(BorderFactory.createEmptyBorder());
    hostWait.add(scroll);
    hostWait.add(Box.createVerticalStrut(16));
    hostWait.add(menuButton("Cancel", this::backToMenu));
    hostWait.setVisible(false);

    hostDeck = new JPanel(new CardLayout());
    Widgets.dark(hostDeck);
    hostDeck.add(hostForm, "form");
    hostDeck.add(hostWait, "wait");
    return hostDeck;
  }

  private void showHostForm() {
    ((CardLayout) hostDeck.getLayout()).show(hostDeck, "form");
  }

  private void showHostWait() {
    ((CardLayout) hostDeck.getLayout()).show(hostDeck, "wait");
  }

  private JPanel joinPanel() {
    joinNameField = Widgets.field("Guest", 16);
    joinAddressField = Widgets.field("", 16);
    joinPortField = Widgets.field(Integer.toString(Protocol.DEFAULT_PORT), 6);
    joinList = new JList<>(foundGames);
    joinList.setBackground(Theme.FIELD);
    joinList.setForeground(Theme.TEXT);
    joinList.setFont(Theme.ui(16));
    joinList.setSelectionBackground(Theme.BUTTON_HOVER);
    joinList.setSelectionForeground(Theme.TEXT);
    joinList.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index,
          boolean selected, boolean focus) {
        super.getListCellRendererComponent(list, value, index, selected, focus);
        if (value instanceof Discovery.Advertisement advertisement) {
          setText(advertisement.label());
        }
        setBackground(selected ? Theme.BUTTON_HOVER : Theme.FIELD);
        setForeground(Theme.TEXT);
        setBorder(new EmptyBorder(6, 8, 6, 8));
        return this;
      }
    });
    joinList.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (event.getClickCount() == 2) {
          joinSelected();
        }
      }
    });

    JPanel panel = column();
    panel.add(Box.createVerticalStrut(24));
    panel.add(Widgets.title("Join"));
    panel.add(Box.createVerticalStrut(8));
    panel.add(Widgets.muted("Games on this network appear below. You can also type an address."));
    panel.add(Box.createVerticalStrut(12));
    panel.add(labeled("Your name", joinNameField));
    panel.add(Box.createVerticalStrut(8));
    JScrollPane scroll = new JScrollPane(joinList);
    scroll.setPreferredSize(new Dimension(460, 160));
    scroll.getViewport().setBackground(Theme.FIELD);
    panel.add(scroll);
    panel.add(Box.createVerticalStrut(8));
    panel.add(menuButton("Join selected game", this::joinSelected));
    panel.add(Box.createVerticalStrut(12));
    panel.add(labeled("Address", joinAddressField));
    panel.add(Box.createVerticalStrut(8));
    panel.add(labeled("Port", joinPortField));
    panel.add(Box.createVerticalStrut(8));
    panel.add(menuButton("Join with this address", this::joinManual));
    panel.add(Box.createVerticalStrut(10));
    panel.add(menuButton("Back", this::backToMenu));
    panel.add(Box.createVerticalGlue());
    return panel;
  }

  private JPanel gamePanel() {
    blackNameLabel.setForeground(Theme.MUTED);
    whiteNameLabel.setForeground(Theme.MUTED);
    blackNameLabel.setFont(Theme.ui(14));
    whiteNameLabel.setFont(Theme.ui(14));
    blackCountLabel.setForeground(Theme.TEXT);
    whiteCountLabel.setForeground(Theme.TEXT);
    blackCountLabel.setFont(Theme.display(36));
    whiteCountLabel.setFont(Theme.display(36));
    fillScore(blackCard, "Black", blackNameLabel, blackCountLabel, true);
    fillScore(whiteCard, "White", whiteNameLabel, whiteCountLabel, false);

    status.setForeground(Theme.TEXT);
    status.setFont(Theme.uiBold(20));
    meta.setForeground(Theme.MUTED);
    meta.setFont(Theme.ui(14));

    JPanel scores = new JPanel(new BorderLayout());
    Widgets.dark(scores);
    scores.setBorder(new EmptyBorder(12, 20, 0, 20));
    scores.add(blackCard, BorderLayout.WEST);
    JPanel centerStatus = new JPanel();
    centerStatus.setLayout(new BoxLayout(centerStatus, BoxLayout.Y_AXIS));
    Widgets.dark(centerStatus);
    status.setAlignmentX(Component.CENTER_ALIGNMENT);
    meta.setAlignmentX(Component.CENTER_ALIGNMENT);
    centerStatus.add(status);
    centerStatus.add(Box.createVerticalStrut(4));
    centerStatus.add(meta);
    scores.add(centerStatus, BorderLayout.CENTER);
    scores.add(whiteCard, BorderLayout.EAST);

    undoButton.addActionListener(event -> onUndo());
    resignButton.addActionListener(event -> onResign());
    newGameButton.addActionListener(event -> onNewGame());
    JButton menu = Widgets.button("Main menu");
    menu.addActionListener(event -> backToMenu());

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
    Widgets.dark(actions);
    actions.add(newGameButton);
    actions.add(undoButton);
    actions.add(resignButton);
    actions.add(menu);

    JPanel panel = new JPanel(new BorderLayout());
    Widgets.dark(panel);
    panel.add(scores, BorderLayout.NORTH);
    panel.add(boardPanel, BorderLayout.CENTER);
    panel.add(actions, BorderLayout.SOUTH);
    return panel;
  }

  private void startComputer() {
    difficulty = (Difficulty) difficultyBox.getSelectedItem();
    boolean playBlack = colorBox.getSelectedIndex() == 0;
    beginLocal(Mode.COMPUTER, playBlack ? Disc.BLACK : Disc.WHITE, "You", "Computer");
  }

  private void startHotseat() {
    beginLocal(Mode.HOTSEAT, Disc.BLACK, "Black", "White");
  }

  private void beginLocal(Mode nextMode, Disc you, String black, String white) {
    shutdownNet();
    mode = nextMode;
    humanDisc = you;
    blackName = black;
    whiteName = white;
    game = new Game();
    thinking = false;
    session++;
    boardPanel.snap();
    showCard("game");
    if (!maybeThink()) {
      refresh();
    }
  }

  private void startHosting() {
    Integer port = portOrWarn(hostPortField.getText());
    if (port == null) {
      return;
    }
    shutdownNet();
    localName = hostNameField.getText();
    Disc color = hostPlaysBlack.isSelected() ? Disc.BLACK : Disc.WHITE;
    host = new MatchHost(localName, color, port);
    hostWaitStatus.setText("Opening port " + port + "…");
    hostAddresses.setText("");
    showHostWait();
    showCard("host");
    host.start(new MatchListener() {
      @Override
      public void onWaiting(int boundPort, List<String> addresses) {
        ui(() -> {
          StringBuilder text = new StringBuilder();
          for (String address : addresses) {
            text.append(address).append(':').append(boundPort).append('\n');
          }
          hostAddresses.setText(text.toString().trim());
          hostWaitStatus.setText("Waiting for the other player…");
        });
      }

      @Override
      public void onNotice(String message) {
        ui(() -> {
          hostWaitStatus.setText(message);
          if (mode == Mode.LAN) {
            status.setText(message);
          }
        });
      }

      @Override
      public void onReady(Disc yourColor, String opponentName, Game position) {
        ui(() -> beginLan(yourColor, Protocol.sanitizeName(localName), opponentName, position));
      }

      @Override
      public void onPosition(Game position) {
        ui(() -> applyRemote(position));
      }

      @Override
      public void onClosed(String reason) {
        ui(() -> onLanClosed(reason));
      }
    });
  }

  private void startBrowsing() {
    foundGames.clear();
    shutdownBrowser();
    browser = new Discovery();
    try {
      browser.listen(found -> ui(() -> remember(found)));
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this,
          "LAN discovery is unavailable. Type the host address instead.");
    }
  }

  private void remember(Discovery.Advertisement advertisement) {
    for (int index = 0; index < foundGames.size(); index++) {
      Discovery.Advertisement existing = foundGames.get(index);
      if (existing.address().equals(advertisement.address()) && existing.port() == advertisement.port()) {
        foundGames.set(index, advertisement);
        return;
      }
    }
    foundGames.addElement(advertisement);
  }

  private void joinSelected() {
    Discovery.Advertisement selected = joinList.getSelectedValue();
    if (selected == null) {
      JOptionPane.showMessageDialog(this, "Select a game from the list, or type an address.");
      return;
    }
    connectGuest(selected.address(), selected.port(), joinNameField.getText());
  }

  private void joinManual() {
    Integer port = portOrWarn(joinPortField.getText());
    if (port == null) {
      return;
    }
    String address = joinAddressField.getText().trim();
    if (address.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Enter the host address, for example 192.168.1.20.");
      return;
    }
    connectGuest(address, port, joinNameField.getText());
  }

  private void connectGuest(String address, int port, String name) {
    shutdownNet();
    localName = name;
    guest = new MatchGuest();
    guest.connect(address, port, name, new MatchListener() {
      @Override
      public void onReady(Disc yourColor, String opponentName, Game position) {
        ui(() -> beginLan(yourColor, Protocol.sanitizeName(localName), opponentName, position));
      }

      @Override
      public void onPosition(Game position) {
        ui(() -> applyRemote(position));
      }

      @Override
      public void onClosed(String reason) {
        ui(() -> onLanClosed(reason));
      }
    });
  }

  private void beginLan(Disc yourColor, String mine, String opponent, Game position) {
    mode = Mode.LAN;
    humanDisc = yourColor;
    localName = mine;
    if (yourColor == Disc.BLACK) {
      blackName = mine;
      whiteName = opponent;
    } else {
      blackName = opponent;
      whiteName = mine;
    }
    thinking = false;
    session++;
    game = position;
    boardPanel.snap();
    showCard("game");
    refresh();
  }

  private void applyRemote(Game position) {
    if (mode != Mode.LAN) {
      return;
    }
    game = position;
    refresh();
  }

  private void onLanClosed(String reason) {
    if (reason == null || reason.isBlank()) {
      return;
    }
    JOptionPane.showMessageDialog(this, reason);
    if (mode != Mode.LAN || game == null || !game.isFinished()) {
      backToMenu();
    }
  }

  private void onCell(int row, int col) {
    if (thinking || game.isFinished()) {
      return;
    }
    if (mode == Mode.LAN) {
      boolean sent = host != null ? host.submitMove(row, col) : guest != null && guest.submitMove(row, col);
      if (!sent) {
        status.setText("Wait for your turn.");
      }
      return;
    }
    if (mode == Mode.COMPUTER && game.turn() != humanDisc) {
      return;
    }
    if (!game.play(row, col)) {
      status.setText("That square does not flip a disc.");
      return;
    }
    if (!maybeThink()) {
      refresh();
    }
  }

  private boolean maybeThink() {
    if (mode == Mode.COMPUTER && !game.isFinished() && game.turn() != humanDisc) {
      startThinking();
      return true;
    }
    return false;
  }

  private void startThinking() {
    int token = ++session;
    thinking = true;
    refresh();
    SwingWorker<Move, Void> worker = new SwingWorker<>() {
      @Override
      protected Move doInBackground() throws Exception {
        Thread.sleep(180);
        return computer.choose(game.boardCopy(), game.turn(), difficulty, this::isCancelled);
      }

      @Override
      protected void done() {
        if (token != session || disposed) {
          return;
        }
        thinking = false;
        try {
          Move move = get();
          if (move != null && !game.isFinished() && game.turn() != humanDisc) {
            game.play(move.row(), move.col());
          }
        } catch (Exception ignored) {
          // Cancelled or interrupted when leaving the game.
        }
        if (!maybeThink()) {
          refresh();
        }
      }
    };
    worker.execute();
  }

  private void onUndo() {
    if (mode == Mode.LAN || thinking || !game.undo()) {
      return;
    }
    if (mode == Mode.COMPUTER && game.turn() != humanDisc) {
      game.undo();
    }
    session++;
    thinking = false;
    refresh();
  }

  private void onResign() {
    if (game.isFinished() || thinking) {
      return;
    }
    if (mode == Mode.COMPUTER) {
      game.finish("You resigned. Computer wins.");
      session++;
      refresh();
      return;
    }
    if (mode == Mode.LAN) {
      if (host != null) {
        host.resign();
      } else if (guest != null) {
        guest.resign();
      }
    }
  }

  private void onNewGame() {
    if (mode == Mode.LAN) {
      return;
    }
    session++;
    thinking = false;
    game = new Game();
    boardPanel.snap();
    if (!maybeThink()) {
      refresh();
    }
  }

  private void refresh() {
    blackNameLabel.setText(blackName);
    whiteNameLabel.setText(whiteName);
    blackCountLabel.setText(Integer.toString(game.count(Disc.BLACK)));
    whiteCountLabel.setText(Integer.toString(game.count(Disc.WHITE)));
    boolean live = !game.isFinished() && !thinking;
    boolean blackActive = live && game.turn() == Disc.BLACK;
    boolean whiteActive = live && game.turn() == Disc.WHITE;
    blackCard.setBorder(activeBorder(blackActive));
    whiteCard.setBorder(activeBorder(whiteActive));
    status.setText(statusText());
    meta.setText(metaText());
    Disc hints = Disc.EMPTY;
    boolean canClick = false;
    if (!game.isFinished() && !thinking) {
      if (mode == Mode.HOTSEAT) {
        hints = game.turn();
        canClick = true;
      } else if (game.turn() == humanDisc) {
        hints = humanDisc;
        canClick = true;
      }
    }
    boardPanel.show(game.boardCopy(), game.lastRow(), game.lastCol(), hints, canClick);
    undoButton.setVisible(mode != Mode.LAN);
    newGameButton.setVisible(mode != Mode.LAN);
    resignButton.setVisible(mode != Mode.HOTSEAT);
    undoButton.setEnabled(game.canUndo() && !thinking);
    resignButton.setEnabled(!game.isFinished() && !thinking);
  }

  private String statusText() {
    if (thinking) {
      return "Computer is thinking…";
    }
    if (game.endReason() != null) {
      return game.endReason();
    }
    if (game.isFinished()) {
      int black = game.count(Disc.BLACK);
      int white = game.count(Disc.WHITE);
      if (black == white) {
        return "Draw, " + black + " each";
      }
      String winner = black > white ? blackName : whiteName;
      return winner + " wins, " + Math.max(black, white) + " to " + Math.min(black, white);
    }
    String pass = "";
    if (game.opponentPassed()) {
      String skipped = game.turn() == Disc.BLACK ? whiteName : blackName;
      pass = skipped + " has no move and passes. ";
    }
    if (mode == Mode.HOTSEAT) {
      String actor = game.turn() == Disc.BLACK ? blackName : whiteName;
      return pass + actor + " to move";
    }
    if (game.turn() == humanDisc) {
      return pass + "Your turn";
    }
    return pass + "Waiting for " + (game.turn() == Disc.BLACK ? blackName : whiteName);
  }

  private String metaText() {
    String side = humanDisc == Disc.BLACK ? "Black" : "White";
    if (mode == Mode.COMPUTER) {
      return "You are " + side + "   ·   " + difficulty.label();
    }
    if (mode == Mode.LAN) {
      return "You are " + side + "   ·   LAN";
    }
    return "Pass the device on each turn";
  }

  private void backToMenu() {
    session++;
    thinking = false;
    shutdownNet();
    showCard("menu");
  }

  private void shutdownNet() {
    if (host != null) {
      host.close();
      host = null;
    }
    if (guest != null) {
      guest.close();
      guest = null;
    }
    shutdownBrowser();
  }

  private void shutdownBrowser() {
    if (browser != null) {
      browser.close();
      browser = null;
    }
  }

  private void showRules() {
    JTextArea text = new JTextArea("""
        Black starts. On your turn, place a disc so at least one straight line of
        opposing discs is trapped between your new disc and another of your discs.
        Every trapped disc flips to your color.

        If you have no legal move, you pass. The game ends when neither player
        can move. The higher disc count wins.

        Play vs Computer uses Easy, Medium, or Hard search.
        Two players on this device take turns at the same screen.
        For a LAN game, one person hosts and the other joins. Both devices need
        this program and must be on the same network. Discovery uses UDP port
        47821. The game itself uses TCP port 47820 unless you change it.
        If a game does not appear in the list, type the address shown on the host.
        """);
    text.setEditable(false);
    text.setFont(Theme.ui(15));
    text.setBackground(Theme.PANEL);
    text.setForeground(Theme.TEXT);
    text.setBorder(new EmptyBorder(8, 8, 8, 8));
    JOptionPane.showMessageDialog(this, text, "How to play", JOptionPane.INFORMATION_MESSAGE);
  }

  private Integer portOrWarn(String text) {
    try {
      int port = Integer.parseInt(text.trim());
      if (port >= 1 && port <= 65535) {
        return port;
      }
    } catch (NumberFormatException ignored) {
      // The message below covers both empty and non-numeric input.
    }
    JOptionPane.showMessageDialog(this, "Enter a port from 1 to 65535.");
    return null;
  }

  private void showCard(String name) {
    cards.show(root, name);
  }

  private void ui(Runnable action) {
    if (disposed) {
      return;
    }
    SwingUtilities.invokeLater(() -> {
      if (!disposed) {
        action.run();
      }
    });
  }

  private JPanel column() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    Widgets.dark(panel);
    panel.setBorder(new EmptyBorder(8, 24, 24, 24));
    return panel;
  }

  private JButton menuButton(String text, Runnable action) {
    JButton button = Widgets.button(text);
    button.setAlignmentX(Component.CENTER_ALIGNMENT);
    button.setMaximumSize(new Dimension(360, 52));
    button.addActionListener(event -> action.run());
    return button;
  }

  private JPanel optionRow(String leftLabel, JComponent left, String rightLabel, JComponent right) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
    Widgets.dark(row);
    row.add(inlineLabel(leftLabel));
    row.add(left);
    row.add(Box.createHorizontalStrut(12));
    row.add(inlineLabel(rightLabel));
    row.add(right);
    return row;
  }

  private JPanel labeled(String caption, JComponent field) {
    JPanel row = new JPanel(new GridBagLayout());
    Widgets.dark(row);
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.insets = new Insets(0, 8, 0, 8);
    constraints.anchor = GridBagConstraints.LINE_END;
    row.add(inlineLabel(caption), constraints);
    constraints.anchor = GridBagConstraints.LINE_START;
    row.add(field, constraints);
    row.setMaximumSize(new Dimension(520, 48));
    return row;
  }

  private JLabel inlineLabel(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(Theme.MUTED);
    label.setFont(Theme.ui(15));
    return label;
  }

  private JPanel scoreCard() {
    JPanel card = new JPanel();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    card.setBackground(Theme.PANEL);
    card.setBorder(activeBorder(false));
    return card;
  }

  private void fillScore(JPanel card, String side, JLabel name, JLabel count, boolean black) {
    JLabel sideLabel = new JLabel(side);
    sideLabel.setForeground(Theme.MUTED);
    sideLabel.setFont(Theme.uiBold(12));
    sideLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    name.setAlignmentX(Component.CENTER_ALIGNMENT);
    count.setAlignmentX(Component.CENTER_ALIGNMENT);
    JPanel swatch = new JPanel() {
      @Override
      protected void paintComponent(java.awt.Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(black ? Theme.INK : Theme.CREAM);
        g.fillOval(4, 2, 18, 18);
        g.setColor(Theme.GOLD);
        g.drawOval(4, 2, 18, 18);
        g.dispose();
      }
    };
    swatch.setOpaque(false);
    swatch.setPreferredSize(new Dimension(28, 24));
    swatch.setMaximumSize(new Dimension(28, 24));
    swatch.setAlignmentX(Component.CENTER_ALIGNMENT);
    card.add(Box.createVerticalStrut(8));
    card.add(swatch);
    card.add(sideLabel);
    card.add(name);
    card.add(count);
    card.add(Box.createVerticalStrut(8));
    card.setPreferredSize(new Dimension(150, 130));
  }

  private javax.swing.border.Border activeBorder(boolean active) {
    return BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(active ? Theme.GOLD : Theme.PANEL, 2),
        new EmptyBorder(6, 16, 6, 16));
  }

  private void styleCombo(JComboBox<?> combo) {
    combo.setBackground(Theme.FIELD);
    combo.setForeground(Theme.TEXT);
    combo.setFont(Theme.ui(15));
  }

  private void styleRadio(JRadioButton radio) {
    radio.setOpaque(false);
    radio.setForeground(Theme.TEXT);
    radio.setFont(Theme.ui(15));
  }

  private static BufferedImage icon() {
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Theme.FELT_DARK);
    g.fillRoundRect(0, 0, 64, 64, 14, 14);
    g.setColor(Theme.INK);
    g.fillOval(8, 8, 22, 22);
    g.fillOval(34, 34, 22, 22);
    g.setColor(Theme.CREAM);
    g.fillOval(34, 8, 22, 22);
    g.fillOval(8, 34, 22, 22);
    g.dispose();
    return image;
  }

  private enum Mode {
    HOTSEAT,
    COMPUTER,
    LAN
  }
}
