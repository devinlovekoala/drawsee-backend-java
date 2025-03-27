package cn.yifan.drawsee.worker;

import cn.yifan.drawsee.pojo.entity.AiTask;
import cn.yifan.drawsee.pojo.entity.Conversation;
import cn.yifan.drawsee.pojo.entity.Node;
import cn.yifan.drawsee.pojo.entity.User;
import cn.yifan.drawsee.pojo.rabbit.AiTaskMessage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import lombok.Data;
import org.redisson.api.RStream;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @FileName WorkerContext
 * @Description
 * @Author yifan
 * @date 2025-03-08 22:24
 **/

@Data
public class WorkContext {

    private AiTaskMessage aiTaskMessage;

    private AiTask aiTask;

    private User user;

    private Conversation conversation;

    private Node parentNode;

    private RStream<String, Object> redisStream;

    private LinkedList<ChatMessage> history;

    private Node streamNode;

    private Map<String, Object> streamNodeData;

    private Response<AiMessage> streamResponse;

    private AtomicLong tokens;

    private List<Node> nodesToUpdate;

    private Boolean isSendDone;

    public WorkContext(AiTaskMessage aiTaskMessage) {
        this.aiTaskMessage = aiTaskMessage;
        this.nodesToUpdate = new ArrayList<>();
        this.isSendDone = true;
        this.tokens = new AtomicLong(0);
    }

}
