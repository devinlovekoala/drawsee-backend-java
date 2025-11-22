package cn.yifan.drawsee.service.business.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 文档解析器，基于 Apache Tika 抽取文本
 */
@Component
@Slf4j
public class DocumentParser {

    private static final int UNLIMITED_WRITE = -1;

    public ParsedDocument parse(InputStream inputStream, String fileName, String contentType) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(UNLIMITED_WRITE);
        Metadata metadata = new Metadata();
        if (fileName != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        if (contentType != null) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }

        parser.parse(inputStream, handler, metadata);

        Integer pageCount = null;
        String pageCountStr = metadata.get("xmpTPg:NPages");
        if (pageCountStr != null) {
            try {
                pageCount = Integer.parseInt(pageCountStr);
            } catch (NumberFormatException ignore) {
                log.debug("无法解析页数: {}", pageCountStr);
            }
        }

        return ParsedDocument.builder()
            .content(handler.toString())
            .pageCount(pageCount)
            .build();
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class ParsedDocument {
        private String content;
        private Integer pageCount;
    }
}
