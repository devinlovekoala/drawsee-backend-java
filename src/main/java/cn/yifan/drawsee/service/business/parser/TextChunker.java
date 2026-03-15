package cn.yifan.drawsee.service.business.parser;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 简单的文本分块器，根据字符数量进行分段 TODO: 迁移到Python RAG服务 - 分块逻辑应由Python服务统一处理 */
@Component
public class TextChunker {

  @Value("${rag.chunk-size:800}")
  private int chunkSize;

  @Value("${rag.chunk-overlap:200}")
  private int chunkOverlap;

  public List<String> chunk(String text) {
    List<String> result = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return result;
    }

    int effectiveChunkSize = Math.max(chunkSize, 200);
    int effectiveOverlap = Math.max(0, Math.min(chunkOverlap, effectiveChunkSize / 2));

    int start = 0;
    int length = text.length();
    while (start < length) {
      int end = Math.min(length, start + effectiveChunkSize);

      // 尝试在末尾寻找换行或句号作为分割点
      if (end < length) {
        int lastBreak = findBreakPoint(text, start, end);
        if (lastBreak > start) {
          end = lastBreak;
        }
      }

      String chunk = text.substring(start, end).trim();
      if (!chunk.isEmpty()) {
        result.add(chunk);
      }

      if (end >= length) {
        break;
      }

      start = Math.max(end - effectiveOverlap, start + 1);
    }

    return result;
  }

  private int findBreakPoint(String text, int start, int end) {
    int best = -1;
    for (int i = end - 1; i >= start; i--) {
      char c = text.charAt(i);
      if (c == '\n' || c == '\r' || c == '。' || c == '.' || c == '!' || c == '?') {
        best = i + 1;
        break;
      }
    }
    return best;
  }
}
