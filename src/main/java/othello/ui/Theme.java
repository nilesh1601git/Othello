package othello.ui;

import java.awt.Color;
import java.awt.Font;

public final class Theme {
  public static final Color BG = new Color(0x101614);
  public static final Color PANEL = new Color(0x1a2420);
  public static final Color FELT_LIGHT = new Color(0x1b9d5b);
  public static final Color FELT_DARK = new Color(0x0c6e3e);
  public static final Color LINE = new Color(0x084e2c);
  public static final Color GOLD = new Color(0xe0c48a);
  public static final Color CREAM = new Color(0xf6f1e6);
  public static final Color INK = new Color(0x141414);
  public static final Color TEXT = new Color(0xf6f1e6);
  public static final Color MUTED = new Color(0xa9b7ae);
  public static final Color BUTTON = new Color(0x24352e);
  public static final Color BUTTON_HOVER = new Color(0x335246);
  public static final Color FIELD = new Color(0x0e1613);

  private Theme() {
  }

  public static Font display(int size) {
    return new Font("Serif", Font.BOLD, size);
  }

  public static Font ui(int size) {
    return new Font("SansSerif", Font.PLAIN, size);
  }

  public static Font uiBold(int size) {
    return new Font("SansSerif", Font.BOLD, size);
  }
}
