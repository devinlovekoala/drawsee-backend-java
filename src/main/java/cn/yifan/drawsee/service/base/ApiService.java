package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.constant.ApiUrl;
import cn.yifan.drawsee.exception.ApiError;
import cn.yifan.drawsee.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;

/**
 * @FileName ApiService
 * @Description
 * @Author yifan
 * @date 2025-03-21 13:15
 **/

@Service
@Slf4j
public class ApiService {

    @Autowired
    private RestClient restClient;

    public void renderAnimation(Long taskId, Long nodeId, String code) {
        URI uri = UriComponentsBuilder
                .fromUriString(ApiUrl.RENDER_ANIMATION_URL)
                .queryParam("task_id", taskId)
                .queryParam("node_id", nodeId)
                .queryParam("code", code)
                .build()
                .toUri();
        
        log.info("发送渲染请求到Python服务, 任务ID: {}, 节点ID: {}, 请求URL: {}", taskId, nodeId, uri);
        
        try {
            // 发送请求
            restClient.get()
                    .uri(uri)
                    .retrieve()
                    .toBodilessEntity();
            
            log.info("渲染请求发送成功, 任务ID: {}, 节点ID: {}", taskId, nodeId);
        } catch (RestClientException e) {
            log.error("Python渲染服务请求失败, 任务ID: {}, 节点ID: {}, 错误: {}", taskId, nodeId, e.getMessage(), e);
            throw new ApiException(ApiError.SYSTEM_ERROR, "文件不能为空");
        }
    }

}
