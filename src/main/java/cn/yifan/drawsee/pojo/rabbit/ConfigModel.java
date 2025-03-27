package cn.yifan.drawsee.pojo.rabbit;

import lombok.Data;

import java.util.List;

/**
 * @FileName ConfigModel
 * @Description
 * @Author yifan
 * @date 2025-01-29 17:33
 **/

@Data
public class ConfigModel {

    private List<ConfigQueue> queues;
    private ConfigExchange exchange;

}
