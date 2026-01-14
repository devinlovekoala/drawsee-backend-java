package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.util.PdfUtils;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.yifan.drawsee.parser.CircuitImageNetlistParser;
import cn.yifan.drawsee.pojo.entity.CircuitDesign;
import cn.yifan.drawsee.service.base.PromptService;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @FileName PdfMultimodalService
 * @Description PDF多模态分析服务，支持文本和图片联合分析
 * @Author yifan
 * @date 2025-12-05
 **/

@Service
@Slf4j
public class PdfMultimodalService {

    @Autowired
    private ChatLanguageModel doubaoVisionChatLanguageModel;

    @Autowired
    private MinioService minioService;

    @Autowired
    private PromptService promptService;

    @Autowired
    private CircuitImageNetlistParser circuitImageNetlistParser;

    /**
     * 多模态分析结果
     */
    public static class MultimodalAnalysis {
        private String textContent;
        private List<String> imageAnalysis;
        private String combinedSummary;
        private List<CircuitDesignResult> circuitDesigns;

        public MultimodalAnalysis() {
            this.imageAnalysis = new ArrayList<>();
            this.circuitDesigns = new ArrayList<>();
        }

        public String getTextContent() { return textContent; }
        public void setTextContent(String textContent) { this.textContent = textContent; }
        public List<String> getImageAnalysis() { return imageAnalysis; }
        public void setImageAnalysis(List<String> imageAnalysis) { this.imageAnalysis = imageAnalysis; }
        public String getCombinedSummary() { return combinedSummary; }
        public void setCombinedSummary(String combinedSummary) { this.combinedSummary = combinedSummary; }
        public List<CircuitDesignResult> getCircuitDesigns() { return circuitDesigns; }
        public void setCircuitDesigns(List<CircuitDesignResult> circuitDesigns) { this.circuitDesigns = circuitDesigns; }
    }

    /**
     * 单页电路图解析结果
     */
    public static class CircuitDesignResult {
        private int pageNo;
        private CircuitDesign circuitDesign;
        private String netlist;

        public CircuitDesignResult(int pageNo, CircuitDesign circuitDesign, String netlist) {
            this.pageNo = pageNo;
            this.circuitDesign = circuitDesign;
            this.netlist = netlist;
        }

        public int getPageNo() { return pageNo; }
        public CircuitDesign getCircuitDesign() { return circuitDesign; }
        public String getNetlist() { return netlist; }
    }

    /**
     * 对PDF进行多模态分析
     * @param pdfInputStream PDF输入流
     * @param maxImagePages 最多分析的图片页数（选择最复杂的页面）
     * @return 多模态分析结果
     */
    public MultimodalAnalysis analyzePdfMultimodal(InputStream pdfInputStream, int maxImagePages) {
        MultimodalAnalysis result = new MultimodalAnalysis();

        try {
            // 将InputStream转换为字节数组，以便重复使用
            byte[] pdfBytes = readAllBytes(pdfInputStream);

            // 1. 提取文本内容
            String textContent = extractTextContent(new ByteArrayInputStream(pdfBytes));
            result.setTextContent(textContent);
            log.info("PDF文本提取完成，长度: {}", textContent != null ? textContent.length() : 0);

            // 2. 选择最复杂的页面进行图片分析
            List<Integer> topPages = PdfUtils.selectTopComplexPages(
                new ByteArrayInputStream(pdfBytes),
                120, // 降低DPI以提升速度
                Math.min(1, maxImagePages) // 仅取1页，降低耗时
            );
            log.info("选择了{}个复杂页面进行视觉分析: {}", topPages.size(), topPages);

            // 3. 渲染选中的页面并进行视觉分析
            List<BufferedImage> images = PdfUtils.renderPages(
                new ByteArrayInputStream(pdfBytes),
                150,
                topPages::contains
            );

            for (int i = 0; i < images.size(); i++) {
                BufferedImage img = images.get(i);
                int pageNo = topPages.get(i);

                String base64Image = convertImageToBase64(img);

                String imageAnalysis = analyzeImage(base64Image, pageNo);
                if (imageAnalysis != null && !imageAnalysis.isBlank()) {
                    result.getImageAnalysis().add(
                        String.format("[第%d页图像分析]\n%s", pageNo + 1, imageAnalysis)
                    );
                }

                CircuitDesignResult designResult = extractCircuitDesign(base64Image, pageNo);
                if (designResult != null) {
                    result.getCircuitDesigns().add(designResult);
                    log.info("第{}页电路图成功转为结构化设计: elements={}, connections={}",
                             pageNo + 1,
                             designResult.getCircuitDesign().getElements() != null
                                 ? designResult.getCircuitDesign().getElements().size() : 0,
                             designResult.getCircuitDesign().getConnections() != null
                                 ? designResult.getCircuitDesign().getConnections().size() : 0);
                }
            }

            log.info("完成{}张图片的视觉分析", result.getImageAnalysis().size());

            // 4. 生成综合摘要
            String combinedSummary = generateCombinedSummary(result);
            result.setCombinedSummary(combinedSummary);

        } catch (Exception e) {
            log.error("PDF多模态分析失败", e);
        }

        return result;
    }

    /**
     * 从MinIO URL获取PDF并进行多模态分析
     * @param pdfUrl MinIO预签名URL
     * @param maxImagePages 最多分析的图片页数
     * @return 多模态分析结果
     */
    public MultimodalAnalysis analyzePdfFromUrl(String pdfUrl, int maxImagePages) {
        try {
            String objectName = extractObjectNameFromUrl(pdfUrl);
            if (objectName == null) {
                log.warn("无法从URL提取对象名称: {}", pdfUrl);
                return null;
            }

            try (InputStream pdfStream = minioService.getObjectStream(objectName)) {
                return analyzePdfMultimodal(pdfStream, maxImagePages);
            }
        } catch (Exception e) {
            log.error("从URL分析PDF失败: {}", pdfUrl, e);
            return null;
        }
    }

    /**
     * 提取PDF文本内容
     */
    private String extractTextContent(InputStream pdfInputStream) {
        try {
            String text = PdfUtils.extractAllText(pdfInputStream);
            if (text == null || text.isBlank()) {
                return "";
            }

            // 限制文本长度
            int maxChars = 4000; // 缩短文本以降低下游生成耗时
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars) + "\n...(内容过长已截断)";
            }

            return text.trim();
        } catch (Exception e) {
            log.error("提取PDF文本失败", e);
            return "";
        }
    }

    /**
     * 使用视觉模型分析图片内容
     */
    private String analyzeImage(String base64Image, int pageNo) {
        try {
            // 构造视觉分析提示词
            String prompt = """
                请分析这张来自电路实验文档的图片，识别以下内容：
                1. 电路图：识别电路元件、连接关系、标注参数
                2. 引脚图：识别芯片型号、引脚定义、功能说明
                3. 波形图：识别信号类型、参数标注、测量要点
                4. 实验步骤图：识别操作流程、关键步骤
                5. 表格数据：提取表格中的关键数据和参数

                请用简洁的语言描述图片的核心内容和关键信息，不超过200字。
                """;

            UserMessage userMessage = UserMessage.from(
                TextContent.from(prompt),
                ImageContent.from("data:image/png;base64," + base64Image)
            );

            Response<dev.langchain4j.data.message.AiMessage> response =
                doubaoVisionChatLanguageModel.generate(userMessage);

            String analysis = response.content().text();
            log.info("第{}页图像分析完成，长度: {}", pageNo + 1, analysis.length());

            return analysis;

        } catch (Exception e) {
            log.error("分析图像失败: pageNo={}", pageNo, e);
            return null;
        }
    }

    /**
     * 将电路图图片转为结构化电路设计
     */
    private CircuitDesignResult extractCircuitDesign(String base64Image, int pageNo) {
        try {
            String prompt = promptService.getCircuitImageDesignPrompt();
            UserMessage userMessage = UserMessage.from(
                TextContent.from(prompt),
                ImageContent.from("data:image/png;base64," + base64Image)
            );

            Response<dev.langchain4j.data.message.AiMessage> response =
                doubaoVisionChatLanguageModel.generate(userMessage);
            String netlist = response.content().text();
            CircuitDesign design = circuitImageNetlistParser.parse(netlist);
            return new CircuitDesignResult(pageNo + 1, design, netlist);
        } catch (Exception e) {
            log.warn("第{}页电路图结构化失败: {}", pageNo + 1, e.getMessage());
            return null;
        }
    }

    /**
     * 将BufferedImage转换为Base64编码
     */
    private String convertImageToBase64(BufferedImage image) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * 生成文本和图片的综合摘要
     */
    private String generateCombinedSummary(MultimodalAnalysis analysis) {
        StringBuilder summary = new StringBuilder();

        // 文本摘要
        if (analysis.getTextContent() != null && !analysis.getTextContent().isBlank()) {
            summary.append("【文档文本内容】\n");
            String textSummary = analysis.getTextContent();
            if (textSummary.length() > 2000) {
                textSummary = textSummary.substring(0, 2000) + "...";
            }
            summary.append(textSummary).append("\n\n");
        }

        // 图片分析摘要
        if (!analysis.getImageAnalysis().isEmpty()) {
            summary.append("【重要图表分析】\n");
            for (String imageAnalysis : analysis.getImageAnalysis()) {
                summary.append(imageAnalysis).append("\n\n");
            }
        }

        return summary.toString().trim();
    }

    /**
     * 从MinIO URL中提取对象名称
     */
    private String extractObjectNameFromUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            if (path == null || path.length() <= 1) {
                return null;
            }
            String[] parts = path.split("/", 3);
            if (parts.length < 3) {
                return null;
            }
            return parts[2]; // 返回 bucket 后面的对象名称
        } catch (Exception e) {
            log.error("提取对象名称失败: url={}", url, e);
            return null;
        }
    }

    /**
     * 读取InputStream的所有字节
     */
    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}
