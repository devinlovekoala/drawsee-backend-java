package cn.yifan.drawsee.service.base;

import cn.yifan.drawsee.constant.ApiUrl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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
        // 发送请求
        restClient.get()
                .uri(uri)
                .retrieve()
                .toBodilessEntity();
    }

}
