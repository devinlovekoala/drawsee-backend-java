package cn.yifan.drawsee.constant;

/**
 * @FileName NodeType
 * @Description 
 * @Author yifan
 * @date 2025-01-31 16:04
 **/

public class NodeType {

    public static final String ROOT = "root";

    public static final String QUERY = "query";

    public static final String ANSWER = "answer";
    
    public static final String ANSWER_POINT = "answer-point";
    
    public static final String ANSWER_DETAIL = "answer-detail";

    public static final String KNOWLEDGE_HEAD = "knowledge-head";

    public static final String KNOWLEDGE_DETAIL = "knowledge-detail";

    public static final String RESOURCE = "resource";
    
    /**
     * 电路画布节点
     */
    public static final String CIRCUIT_CANVAS = "circuit-canvas";
    
    /**
     * 电路分析节点（预热/推荐/追问）
     */
    public static final String CIRCUIT_ANALYZE = "circuit-analyze";
    
    /**
     * 电路分析点节点
     */
    public static final String CIRCUIT_POINT = "circuit-point";

    /**
     * PDF电路实验任务分析点节点
     */
    public static final String PDF_CIRCUIT_POINT = "pdf-circuit-point";

    /**
     * PDF电路实验任务分析详情节点
     */
    public static final String PDF_CIRCUIT_DETAIL = "pdf-circuit-detail";

}
