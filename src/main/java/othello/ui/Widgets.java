package othello.ui;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public final class Widgets {
  private Widgets() {
  }

  public static JButton button(String text) {
    JButton button = new JButton(text) {
      private boolean hover;

      {
        addMouseListener(new java.awt.event.MouseAdapter() {
          @Override
          public void mouseEntered(java.awt.event.MouseEvent event) {
            hover = true;
            repaint();
          }

          @Override
          public void mouseExited(java.awt.event.MouseEvent event) {
            hover = false;
            repaint();
          }
        });
      }

      @Override
      protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getModel().isPressed() || hover ? Theme.BUTTON_HOVER : Theme.BUTTON);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        g.setColor(isEnabled() ? Theme.GOLD : Theme.MUTED);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g.dispose();
        super.paintComponent(graphics);
      }
    };
    button.setContentAreaFilled(false);
    button.setFocusPainted(false);
    button.setBorderPainted(false);
    button.setOpaque(false);
    button.setForeground(Theme.TEXT);
    button.setFont(Theme.uiBold(16));
    button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    button.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 22));
    return button;
  }

  public static JTextField field(String text, int columns) {
    JTextField field = new JTextField(text, columns);
    field.setBackground(Theme.FIELD);
    field.setForeground(Theme.TEXT);
    field.setCaretColor(Theme.GOLD);
    field.setFont(Theme.ui(16));
    field.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.GOLD, 1),
        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    return field;
  }

  public static JTextArea readOnly(String text) {
    JTextArea area = new JTextArea(text);
    area.setEditable(false);
    area.setBackground(Theme.FIELD);
    area.setForeground(Theme.TEXT);
    area.setFont(Theme.ui(16));
    area.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.GOLD, 1),
        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    return area;
  }

  public static JLabel title(String text) {
    JLabel label = new JLabel(text, SwingConstants.CENTER);
    label.setForeground(Theme.TEXT);
    label.setFont(Theme.display(64));
    label.setAlignmentX(Component.CENTER_ALIGNMENT);
    return label;
  }

  public static JLabel muted(String text) {
    JLabel label = new JLabel(text, SwingConstants.CENTER);
    label.setForeground(Theme.MUTED);
    label.setFont(Theme.ui(16));
    label.setAlignmentX(Component.CENTER_ALIGNMENT);
    return label;
  }

  public static void dark(JComponent component) {
    component.setBackground(Theme.BG);
    component.setForeground(Theme.TEXT);
    component.setOpaque(true);
  }
}
