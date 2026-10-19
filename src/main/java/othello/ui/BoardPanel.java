package othello.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiConsumer;
import javax.swing.JPanel;
import javax.swing.Timer;
import othello.model.Board;
import othello.model.Disc;

/**
 * Paints the board and animates each change between positions: new discs drop
 * in, captured discs flip outward from the move, and undone discs shrink away.
 * Positions that arrive during an animation are queued and played in order.
 */
public final class BoardPanel extends JPanel {
  private static final int PLACE_MS = 320;
  private static final int FLIP_MS = 380;
  private static final int REMOVE_MS = 220;
  private static final int STAGGER_MS = 45;
  private static final int REPLY_PAUSE_MS = 260;

  private final BiConsumer<Integer, Integer> onClick;
  private final Deque<Frame> pending = new ArrayDeque<>();
  private final Timer timer = new Timer(15, event -> tick());
  private Board board = new Board();
  private int lastRow = -1;
  private int lastCol = -1;
  private Disc hintDisc = Disc.EMPTY;
  private boolean interactive;
  private int hoverRow = -1;
  private int hoverCol = -1;
  private boolean snapNext = true;

  private Frame animating;
  private CellAnimation[] cells;
  private long animationStart;
  private long animationLength;

  public BoardPanel(BiConsumer<Integer, Integer> onClick) {
    this.onClick = onClick;
    setOpaque(true);
    setBackground(Theme.BG);
    setPreferredSize(new Dimension(680, 700));
    timer.setCoalesce(true);
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        int[] cell = cellAt(event.getX(), event.getY());
        if (cell != null && interactive && animating == null) {
          onClick.accept(cell[0], cell[1]);
        }
      }

      @Override
      public void mouseExited(MouseEvent event) {
        hoverRow = -1;
        hoverCol = -1;
        repaint();
      }

      @Override
      public void mouseMoved(MouseEvent event) {
        int[] cell = cellAt(event.getX(), event.getY());
        int row = cell == null ? -1 : cell[0];
        int col = cell == null ? -1 : cell[1];
        if (row != hoverRow || col != hoverCol) {
          hoverRow = row;
          hoverCol = col;
          repaint();
        }
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  /** The next position is drawn immediately, for a new or resumed game. */
  public void snap() {
    timer.stop();
    pending.clear();
    animating = null;
    cells = null;
    snapNext = true;
  }

  public boolean isAnimating() {
    return animating != null;
  }

  public void show(Board next, int lastMoveRow, int lastMoveCol, Disc hints, boolean canClick) {
    Frame frame = new Frame(next.copy(), lastMoveRow, lastMoveCol,
        hints == null ? Disc.EMPTY : hints, canClick);
    if (snapNext) {
      snapNext = false;
      apply(frame);
      return;
    }
    Frame latest = pending.isEmpty() ? animating : pending.peekLast();
    if (latest != null && latest.board().equals(frame.board())) {
      if (pending.isEmpty()) {
        animating = animating == null ? null : frame;
      } else {
        pending.pollLast();
        pending.addLast(frame);
      }
      return;
    }
    if (latest == null && board.equals(frame.board())) {
      apply(frame);
      return;
    }
    pending.addLast(frame);
    if (animating == null) {
      startNext(false);
    }
  }

  private void apply(Frame frame) {
    board = frame.board();
    lastRow = frame.lastRow();
    lastCol = frame.lastCol();
    hintDisc = frame.hints();
    interactive = frame.canClick();
    repaint();
  }

  private void startNext(boolean chained) {
    Frame frame = pending.pollFirst();
    if (frame == null) {
      animating = null;
      cells = null;
      timer.stop();
      repaint();
      return;
    }
    CellAnimation[] next = new CellAnimation[Board.SIZE * Board.SIZE];
    long length = 0;
    boolean addsDisc = false;
    for (int row = 0; row < Board.SIZE; row++) {
      for (int col = 0; col < Board.SIZE; col++) {
        Disc from = board.get(row, col);
        Disc to = frame.board().get(row, col);
        if (from == to) {
          continue;
        }
        addsDisc |= from == Disc.EMPTY;
        boolean removing = to == Disc.EMPTY;
        int delay = removing || frame.lastRow() < 0
            ? 0
            : (Math.abs(row - frame.lastRow()) + Math.abs(col - frame.lastCol())) * STAGGER_MS;
        int duration = from == Disc.EMPTY ? PLACE_MS : removing ? REMOVE_MS : FLIP_MS;
        next[row * Board.SIZE + col] = new CellAnimation(from, to, delay, duration);
        length = Math.max(length, delay + duration);
      }
    }
    long pause = chained && addsDisc ? REPLY_PAUSE_MS : 0;
    animating = frame;
    cells = next;
    animationStart = System.currentTimeMillis() + pause;
    animationLength = length;
    lastRow = -1;
    lastCol = -1;
    hintDisc = Disc.EMPTY;
    interactive = false;
    timer.start();
    repaint();
  }

  private void tick() {
    if (animating == null) {
      timer.stop();
      return;
    }
    if (System.currentTimeMillis() - animationStart >= animationLength) {
      Frame done = animating;
      animating = null;
      cells = null;
      apply(done);
      if (!pending.isEmpty()) {
        interactive = false;
        hintDisc = Disc.EMPTY;
        startNext(true);
      } else {
        timer.stop();
      }
      return;
    }
    repaint();
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    Geometry geometry = geometry();
    g.setColor(Theme.MUTED);
    g.setFont(Theme.uiBold(13));
    for (int index = 0; index < Board.SIZE; index++) {
      String file = String.valueOf((char) ('a' + index));
      int fileX = geometry.originX + index * geometry.cell + geometry.cell / 2 - 4;
      g.drawString(file, fileX, geometry.originY + geometry.board + 20);
      String rank = String.valueOf(index + 1);
      g.drawString(rank, geometry.originX - 18, geometry.originY + index * geometry.cell + geometry.cell / 2 + 5);
    }
    long elapsed = System.currentTimeMillis() - animationStart;
    for (int row = 0; row < Board.SIZE; row++) {
      for (int col = 0; col < Board.SIZE; col++) {
        int x = geometry.originX + col * geometry.cell;
        int y = geometry.originY + row * geometry.cell;
        g.setColor(((row + col) % 2 == 0) ? Theme.FELT_LIGHT : Theme.FELT_DARK);
        g.fillRect(x, y, geometry.cell, geometry.cell);
        g.setColor(Theme.LINE);
        g.drawRect(x, y, geometry.cell, geometry.cell);
        CellAnimation animation = cells == null ? null : cells[row * Board.SIZE + col];
        if (animation != null) {
          paintAnimated(g, x, y, geometry.cell, animation, elapsed);
          continue;
        }
        Disc disc = board.get(row, col);
        if (disc != Disc.EMPTY) {
          paintDisc(g, x, y, geometry.cell, disc, row == lastRow && col == lastCol, 1f, 1f, 0);
        } else if (hintDisc != Disc.EMPTY && board.isLegal(row, col, hintDisc)) {
          paintHint(g, x, y, geometry.cell, row == hoverRow && col == hoverCol);
        }
      }
    }
    g.setColor(Theme.GOLD);
    g.setStroke(new BasicStroke(3f));
    g.drawRoundRect(geometry.originX - 2, geometry.originY - 2, geometry.board + 4, geometry.board + 4, 8, 8);
    g.dispose();
  }

  private void paintAnimated(Graphics2D g, int x, int y, int cell, CellAnimation animation,
      long elapsed) {
    float t = clamp((elapsed - animation.delay()) / (float) animation.duration());
    if (animation.from() == Disc.EMPTY) {
      if (t <= 0f) {
        return;
      }
      float scale = easeOutBack(t);
      int lift = (int) ((1f - t) * cell * 0.12f);
      paintDisc(g, x, y, cell, animation.to(), false, scale, scale, lift);
      return;
    }
    if (animation.to() == Disc.EMPTY) {
      float scale = 1f - t;
      if (scale <= 0f) {
        return;
      }
      Composite original = g.getComposite();
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, scale));
      paintDisc(g, x, y, cell, animation.from(), false, scale, scale, 0);
      g.setComposite(original);
      return;
    }
    float width = (float) Math.abs(Math.cos(Math.PI * t));
    Disc face = t < 0.5f ? animation.from() : animation.to();
    float rise = (float) Math.sin(Math.PI * t);
    paintDisc(g, x, y, cell, face, false, Math.max(0.04f, width), 1f + rise * 0.06f,
        (int) (rise * cell * 0.06f));
  }

  private void paintDisc(Graphics2D g, int x, int y, int cell, Disc disc, boolean last,
      float scaleX, float scaleY, int lift) {
    int full = (int) (cell * 0.76);
    int width = Math.max(1, Math.round(full * scaleX));
    int height = Math.max(1, Math.round(full * scaleY));
    int ox = x + (cell - width) / 2;
    int oy = y + (cell - height) / 2 - lift;
    g.setColor(new Color(0, 0, 0, 70));
    g.fillOval(ox + 2, oy + 3 + lift, width, height);
    float cx = ox + width * 0.35f;
    float cy = oy + height * 0.30f;
    float radius = Math.max(1f, Math.max(width, height) * 0.85f);
    if (disc == Disc.BLACK) {
      g.setPaint(new RadialGradientPaint(cx, cy, radius,
          new float[] {0f, 1f},
          new Color[] {new Color(0x5a5a5a), Theme.INK}));
    } else {
      g.setPaint(new RadialGradientPaint(cx, cy, radius,
          new float[] {0f, 1f},
          new Color[] {Color.WHITE, new Color(0xd9d0c1)}));
    }
    g.fillOval(ox, oy, width, height);
    if (last) {
      int dot = Math.max(5, full / 7);
      g.setColor(Theme.GOLD);
      g.fillOval(ox + (width - dot) / 2, oy + (height - dot) / 2, dot, dot);
    }
  }

  private void paintHint(Graphics2D g, int x, int y, int cell, boolean hover) {
    int diameter = hover ? (int) (cell * 0.34) : (int) (cell * 0.22);
    int ox = x + (cell - diameter) / 2;
    int oy = y + (cell - diameter) / 2;
    g.setColor(new Color(224, 196, 138, hover ? 210 : 120));
    g.fillOval(ox, oy, diameter, diameter);
  }

  private static float clamp(float value) {
    return Math.max(0f, Math.min(1f, value));
  }

  private static float easeOutBack(float t) {
    float c1 = 1.70158f;
    float c3 = c1 + 1f;
    float shifted = t - 1f;
    return 1f + c3 * shifted * shifted * shifted + c1 * shifted * shifted;
  }

  private int[] cellAt(int x, int y) {
    Geometry geometry = geometry();
    int col = (x - geometry.originX) / geometry.cell;
    int row = (y - geometry.originY) / geometry.cell;
    if (x < geometry.originX || y < geometry.originY
        || row < 0 || col < 0 || row >= Board.SIZE || col >= Board.SIZE) {
      return null;
    }
    return new int[] {row, col};
  }

  private Geometry geometry() {
    int label = 28;
    int margin = 16;
    int available = Math.min(getWidth() - margin * 2 - label, getHeight() - margin * 2 - label);
    int cell = Math.max(8, available / Board.SIZE);
    int boardSize = cell * Board.SIZE;
    int originX = (getWidth() - boardSize) / 2 + 6;
    int originY = (getHeight() - boardSize) / 2 - 6;
    return new Geometry(originX, originY, cell, boardSize);
  }

  private record Frame(Board board, int lastRow, int lastCol, Disc hints, boolean canClick) {
  }

  private record CellAnimation(Disc from, Disc to, int delay, int duration) {
  }

  private record Geometry(int originX, int originY, int cell, int board) {
  }
}
