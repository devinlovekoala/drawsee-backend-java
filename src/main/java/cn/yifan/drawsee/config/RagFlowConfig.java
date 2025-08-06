package cn.yifan.drawsee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RAGFlow配置类
 * 用于连接RAGFlow服务并提供API调用能力
 * 
 * @author devin
 * @date 2025-05-08
 */
@Configuration
@ConfigurationProperties(prefix = "ragflow")
@Data
public class RagFlowConfig {
    
    /**
     * RAGFlow API 端点地址
     */
    private String apiEndpoint;
    
    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeout;
    
    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout;
    
    /**
     * 处理超时时间（毫秒）
     */
    private int processingTimeout;
    
    /**
     * 是否启用RAGFlow
     */
    private boolean enabled;
    
    /**
     * API认证密钥
     */
    private String apiKey;
    
    /**
     * MySQL数据库配置
     */
    private MySQLConfig mysql = new MySQLConfig();
    
    /**
     * 向量数据库配置
     */
    private VectorStore vectorStore = new VectorStore();
    
    /**
     * 嵌入模型配置
     */
    private Embedding embedding = new Embedding();
    
    /**
     * 文档处理配置
     */
    private Document document = new Document();
    
    /**
     * LLM模型配置
     */
    private LlmConfig llm = new LlmConfig();
    
    /**
     * MinIO对象存储配置
     */
    private MinioConfig minio = new MinioConfig();
    
    /**
     * S3对象存储配置
     */
    private S3Config s3 = new S3Config();
    
    /**
     * OSS对象存储配置
     */
    private OssConfig oss = new OssConfig();
    
    @Data
    public static class MySQLConfig {
        /**
         * 数据库名称
         */
        private String name = "rag_flow";
        
        /**
         * 数据库主机
         */
        private String host = "mysql";
        
        /**
         * 数据库端口
         */
        private int port = 3306;
        
        /**
         * 数据库用户名
         */
        private String user = "root";
        
        /**
         * 数据库密码
         */
        private String password;
        
        /**
         * 最大连接数
         */
        private int maxConnections = 100;
    }
    
    @Data
    public static class VectorStore {
        /**
         * 向量数据库类型，支持milvus/qdrant/pinecone/elasticsearch/infinity
         */
        private String type;
        
        /**
         * 向量数据库主机地址
         */
        private String host;
        
        /**
         * 向量数据库端口
         */
        private int port;
        
        /**
         * 向量数据库用户名
         */
        private String username;
        
        /**
         * 向量数据库密码
         */
        private String password;
    }
    
    @Data
    public static class Embedding {
        /**
         * 嵌入模型名称
         */
        private String model;
        
        /**
         * 向量维度
         */
        private int dimension;
        
        /**
         * 模型运行设备
         */
        private String device;
        
        /**
         * 本地模型路径
         */
        private String modelPath;
        
        /**
         * 嵌入API服务地址
         */
        private String apiUrl;
        
        /**
         * 嵌入API服务密钥
         */
        private String apiKey;
        
        /**
         * 嵌入API提供商
         */
        private String apiProvider;
        
        /**
         * 模型使用代理地址
         */
        private String hfEndpoint;
    }
    
    @Data
    public static class Document {
        /**
         * 文档分块大小
         */
        private int chunkSize;
        
        /**
         * 分块重叠大小
         */
        private int chunkOverlap;
        
        /**
         * 分块策略
         */
        private String chunkStrategy;
        
        /**
         * 文档存储类型 (file/s3/minio/oss)
         */
        private String storeType;
        
        /**
         * 文档存储路径
         */
        private String storePath;
        
        /**
         * 对象存储前缀
         */
        private String objectPrefix;
        
        /**
         * 最大文件大小限制（字节）
         */
        private long maxContentLength = 134217728; // 默认128MB
    }
    
    @Data
    public static class MinioConfig {
        /**
         * MinIO服务地址
         */
        private String host;
        
        /**
         * MinIO访问密钥
         */
        private String user;
        
        /**
         * MinIO秘密密钥
         */
        private String password;
        
        /**
         * MinIO存储桶名称
         */
        private String bucket;
    }
    
    @Data
    public static class S3Config {
        /**
         * S3访问密钥
         */
        private String accessKey;
        
        /**
         * S3秘密密钥
         */
        private String secretKey;
        
        /**
         * S3服务端点URL
         */
        private String endpointUrl;
        
        /**
         * S3区域
         */
        private String region;
        
        /**
         * S3存储桶名称
         */
        private String bucket;
        
        /**
         * 签名版本
         */
        private String signatureVersion;
        
        /**
         * 寻址风格
         */
        private String addressingStyle;
        
        /**
         * 前缀路径
         */
        private String prefixPath;
    }
    
    @Data
    public static class OssConfig {
        /**
         * OSS访问密钥
         */
        private String accessKey;
        
        /**
         * OSS秘密密钥
         */
        private String secretKey;
        
        /**
         * OSS服务端点URL
         */
        private String endpointUrl;
        
        /**
         * OSS区域
         */
        private String region;
        
        /**
         * OSS存储桶名称
         */
        private String bucket;
        
        /**
         * 前缀路径
         */
        private String prefixPath;
    }
    
    @Data
    public static class LlmConfig {
        /**
         * LLM提供商
         */
        private String provider;
        
        /**
         * API密钥
         */
        private String apiKey;
        
        /**
         * 模型名称
         */
        private String modelName;
        
        /**
         * 最大输出Token数
         */
        private int maxTokens;
        
        /**
         * 温度参数
         */
        private double temperature;
        
        /**
         * 模型工厂
         */
        private String factory;
    }
    
    /**
     * 创建RestTemplate用于API调用
     */
    @Bean(name = "ragFlowRestTemplate")
    public RestTemplate ragFlowRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        
        return new RestTemplate(factory);
    }
    
    /**
     * 创建替代RestTemplate用于API调用
     * 主要为了解决Bean命名冲突问题
     */
    @Bean(name = "ragFlowRestTemplateAlt")
    public RestTemplate ragFlowRestTemplateAlt() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        
        return new RestTemplate(factory);
    }
} 