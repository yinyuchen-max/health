package com.health.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 启动时加载模板；外部目录按文件覆盖内置模板，重启后生效。 */
@Service
public class PromptService {
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z][a-zA-Z0-9_]*)\\s*}}");
    private static final Map<String, Set<String>> CONTRACTS = Map.of(
            "chat-system", Set.of(),
            "chat-user-context", Set.of("healthRecords", "sportRecords"),
            "health-report-system", Set.of(),
            "health-report-user", Set.of("userProfile", "latestMetrics", "healthRecords",
                    "sportRecords", "retrievedKnowledge", "historyRecords"),
            "appointment-extract-system", Set.of("currentDate", "context", "departments"));

    private final Map<String, String> templates;

    public PromptService(@Value("${ai.prompts.directory:./prompts}") String directory) {
        Path externalDirectory = directory == null || directory.isBlank() ? null : Path.of(directory);
        if (externalDirectory != null && Files.exists(externalDirectory) && !Files.isDirectory(externalDirectory)) {
            throw new IllegalStateException("Prompt 外部路径必须是目录: " + externalDirectory);
        }
        Map<String, String> loaded = new HashMap<>();
        CONTRACTS.forEach((name, variables) -> {
            String template = load(name, externalDirectory);
            Set<String> actual = new HashSet<>();
            VARIABLE.matcher(template).results().forEach(match -> actual.add(match.group(1)));
            if (template.isBlank() || !actual.equals(variables)) {
                throw new IllegalStateException("Prompt 模板为空或变量不匹配: " + name
                        + "，需要 " + variables + "，实际 " + actual);
            }
            loaded.put(name, template);
        });
        this.templates = Map.copyOf(loaded);
    }

    public String render(String name, Map<String, ?> variables) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("未知 Prompt 模板: " + name);
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Prompt " + name + " 缺少变量: " + key);
            }
            // 仅遍历模板一次：用户数据中的 {{变量}}、$ 和反斜杠全部原样保留。
            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String load(String name, Path directory) {
        Path external = directory == null ? null : directory.resolve(name + ".md");
        try {
            if (external != null && Files.exists(external)) {
                return Files.readString(external, StandardCharsets.UTF_8);
            }
            try (InputStream input = new ClassPathResource("prompts/" + name + ".md").getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载 Prompt 模板: " + name, exception);
        }
    }
}
