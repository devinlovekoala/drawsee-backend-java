package cn.yifan.drawsee.schedule;

import cn.yifan.drawsee.constant.NodeType;
import cn.yifan.drawsee.mapper.NodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * 启动时清理 node 表中遗留的旧类型节点（例如历史的 "circuit-point"）。
 * 此操作使用软删除（设置 is_deleted = 1），保证可回滚与审计。
 */
@Component
@Slf4j
public class NodeTableCleaner {

    @Autowired
    private NodeMapper nodeMapper;

    /**
     * 在应用准备就绪后运行一次清理，避免在容器尚未初始化时执行数据库操作。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanLegacyCircuitPointNodes() {
        try {
            int affected = nodeMapper.softDeleteByType(NodeType.CIRCUIT_POINT);
            if (affected > 0) {
                log.info("NodeTableCleaner: 标记 {} 条 'circuit-point' 节点为已删除", affected);
            } else {
                log.info("NodeTableCleaner: 未发现 'circuit-point' 节点需要清理");
            }
        } catch (Exception e) {
            log.error("NodeTableCleaner 执行失败: {}", e.getMessage(), e);
        }
    }

}
