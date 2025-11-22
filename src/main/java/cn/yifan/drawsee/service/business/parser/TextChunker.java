package cn.yifan.drawsee.service.business.parser;

import cn.yifan.drawsee.config.WeaviateConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单的文本分块器，根据字符数量进行分段
 */
@Component
public class TextChunker {

    private final WeaviateConfig weaviateConfig;

    @Autowired
    public TextChunker(WeaviateConfig weaviateConfig) {
        this.weaviateConfig = weaviateConfig;
    }

    public List<String> chunk(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        int chunkSize = Math.max(weaviateConfig.getChunkSize(), 200);
        int overlap = Math.max(0, Math.min(weaviateConfig.getChunkOverlap(), chunkSize / 2));

        int start = 0;
        int length = text.length();
        while (start < length) {
            int end = Math.min(length, start + chunkSize);

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

            start = Math.max(end - overlap, start + 1);
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
