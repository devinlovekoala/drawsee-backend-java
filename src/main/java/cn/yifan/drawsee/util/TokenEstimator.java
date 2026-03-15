package cn.yifan.drawsee.util;

/** 轻量Token估算与裁剪工具（避免硬编码字符截断） */
public final class TokenEstimator {

  private TokenEstimator() {}

  public static int estimateTokens(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    double tokenCount = 0.0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (Character.isWhitespace(ch)) {
        continue;
      }
      if (isCjk(ch)) {
        tokenCount += 1.0;
      } else {
        tokenCount += 0.25;
      }
    }
    return (int) Math.ceil(tokenCount);
  }

  public static String trimToTokenBudget(String text, int maxTokens) {
    if (text == null || text.isEmpty() || maxTokens <= 0) {
      return "";
    }
    if (estimateTokens(text) <= maxTokens) {
      return text;
    }
    StringBuilder builder = new StringBuilder();
    double tokenCount = 0.0;
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      double delta = 0.0;
      if (!Character.isWhitespace(ch)) {
        delta = isCjk(ch) ? 1.0 : 0.25;
      }
      if (tokenCount + delta > maxTokens) {
        break;
      }
      builder.append(ch);
      tokenCount += delta;
    }
    String result = builder.toString().trim();
    if (!result.endsWith("...")) {
      result = result + "...";
    }
    return result;
  }

  public static String normalizeWhitespace(String text) {
    if (text == null) {
      return "";
    }
    return text.replaceAll("\\s+", " ").trim();
  }

  private static boolean isCjk(char ch) {
    Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
        || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
        || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
        || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
        || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
        || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
        || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
        || block == Character.UnicodeBlock.HIRAGANA
        || block == Character.UnicodeBlock.KATAKANA
        || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
  }
}
