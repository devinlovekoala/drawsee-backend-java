package cn.yifan.drawsee.util;

import cn.yifan.drawsee.annotation.PromptParam;
import cn.yifan.drawsee.annotation.PromptResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @FileName PromptServiceInvocationHandler
 * @Description
 * @Author devin
 * @date 2025-03-09 23:09
 **/

public class PromptServiceInvocationHandler implements InvocationHandler {

    private final ResourceLoader resourceLoader;

    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>();

    public PromptServiceInvocationHandler(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        // 1. 解析方法注解
        PromptResource promptRes = method.getAnnotation(PromptResource.class);
        String templatePath = promptRes.fromResource();

        // 2. 加载模板文件（带缓存机制）
        String templateContent = loadTemplate(templatePath);

        // 3. 参数绑定
        Parameter[] parameters = method.getParameters();
        Map<String, Object> variables = new ConcurrentHashMap<>();
        for (int i = 0; i < parameters.length; i++) {
            PromptParam paramAnno = parameters[i].getAnnotation(PromptParam.class);
            variables.put(paramAnno.value(), args[i]);
        }

        // 4. 模板渲染（支持{{variable}}格式）
        return renderTemplate(templateContent, variables);
    }

    private String loadTemplate(String path) {
        return templateCache.computeIfAbsent(path, p -> {
            try {
                Resource resource = resourceLoader.getResource("classpath:" + p);
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载模板失败: " + p, e);
            }
        });
    }

    private String renderTemplate(String template, Map<String,Object> vars) {
        for (Map.Entry<String,Object> entry : vars.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}",
                    entry.getValue().toString());
        }
        return template;
    }

}
