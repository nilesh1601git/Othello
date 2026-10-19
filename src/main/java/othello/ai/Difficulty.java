package othello.ai;

public enum Difficulty {
  EASY("Easy", 1, 150),
  MEDIUM("Medium", 4, 700),
  HARD("Hard", 7, 1_400);

  private final String label;
  private final int maxDepth;
  private final long budgetMillis;

  Difficulty(String label, int maxDepth, long budgetMillis) {
    this.label = label;
    this.maxDepth = maxDepth;
    this.budgetMillis = budgetMillis;
  }

  public String label() {
    return label;
  }

  public int maxDepth() {
    return maxDepth;
  }

  public long budgetMillis() {
    return budgetMillis;
  }

  @Override
  public String toString() {
    return label;
  }
}
