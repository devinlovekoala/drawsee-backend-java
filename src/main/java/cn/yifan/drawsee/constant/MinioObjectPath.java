package cn.yifan.drawsee.constant;

/**
 * @FileName MinioObjectPath
 * @Description
 * @Author yifan
 * @date 2025-03-23 14:25
 **/

public class MinioObjectPath {

    public static final String RECOGNIZE_IMAGE_PATH = "images/recognize/";
    public static final String CIRCUIT_RECOGNIZE_IMAGE_PATH = "images/circuit_recognize/";

    // 知识资源路径
    public static final String KNOWLEDGE_RESOURCE_PATH = "knowledge/resource/";
    public static final String KNOWLEDGE_WORD_PATH = KNOWLEDGE_RESOURCE_PATH + "word/";
    public static final String KNOWLEDGE_PDF_PATH = KNOWLEDGE_RESOURCE_PATH + "pdf/";
    public static final String KNOWLEDGE_MP4_PATH = KNOWLEDGE_RESOURCE_PATH + "mp4/";
    
    // 动画资源路径
    public static final String ANIMATION_PATH = "ai-animation/";

	public static final String DOCUMENT_PAGE_PATH = "document/page/";

    // RAG 知识库相关路径
    public static final String RAG_KNOWLEDGE_BASE_ROOT = "rag/";
    public static final String RAG_DOCUMENT_FOLDER = "documents/";
}
