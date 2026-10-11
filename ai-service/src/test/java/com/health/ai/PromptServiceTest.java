package com.health.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptServiceTest {
    @TempDir Path directory;

    @Test
    void loadsAllBuiltInsAndRendersChineseData() {
        PromptService prompts = new PromptService("");
        assertTrue(prompts.render("chat-system", Map.of()).contains("健康管理顾问"));
        assertTrue(prompts.render("health-report-system", Map.of()).contains("riskAssessments"));
        String report = prompts.render("health-report-user", Map.of(
                "userProfile", "年龄30", "latestMetrics", "心率75", "healthRecords", "健康记录",
                "sportRecords", "游泳30分钟", "retrievedKnowledge", "知识依据", "historyRecords", "历史记录"));
        assertTrue(report.contains("游泳30分钟"));
        assertFalse(report.contains("{{"));
        assertTrue(prompts.render("appointment-extract-system", Map.of(
                "currentDate", "2026-10-02", "context", "{}", "departments", "内科"))
                .contains("今天日期：2026-10-02"));
    }

    @Test
    void externalOverridesAreLoadedOnceAndMissingFilesUseBuiltIns() throws Exception {
        Path file = directory.resolve("chat-system.md");
        Files.writeString(file, "自定义顾问一");
        PromptService prompts = new PromptService(directory.toString());
        assertEquals("自定义顾问一", prompts.render("chat-system", Map.of()));
        assertTrue(prompts.render("health-report-system", Map.of()).contains("riskAssessments"));
        Files.writeString(file, "自定义顾问二");
        assertEquals("自定义顾问一", prompts.render("chat-system", Map.of()));
        assertEquals("自定义顾问二", new PromptService(directory.toString()).render("chat-system", Map.of()));
    }

    @Test
    void variableValuesAreLiteralAndNeverRenderedAgain() {
        PromptService prompts = new PromptService("");
        String literal = "{{sportRecords}} $1 \\ 用户备注";
        String result = prompts.render("chat-user-context", Map.of("healthRecords", literal, "sportRecords", "跑步"));
        assertTrue(result.contains(literal));
        assertTrue(result.contains("跑步"));
    }

    @Test
    void rejectsMissingValuesAndUnknownNames() {
        PromptService prompts = new PromptService("");
        assertThrows(IllegalArgumentException.class, () -> prompts.render("chat-user-context", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> prompts.render("../application", Map.of()));
    }

    @Test
    void rejectsBlankOrIncompatibleOverridesAtStartup() throws Exception {
        Path file = directory.resolve("chat-user-context.md");
        for (String invalid : new String[]{" ", "{{healthRecords}}", "{{healthRecords}}{{sportRecords}}{{typo}}"}) {
            Files.writeString(file, invalid);
            assertThrows(IllegalStateException.class, () -> new PromptService(directory.toString()));
        }
    }

    @Test
    void rejectsInvalidDirectoryAndUnreadableOverrideInsteadOfSilentlyFallingBack() throws Exception {
        Path file = directory.resolve("file.txt");
        Files.writeString(file, "not a directory");
        assertThrows(IllegalStateException.class, () -> new PromptService(file.toString()));
        Files.createDirectory(directory.resolve("chat-system.md"));
        assertThrows(IllegalStateException.class, () -> new PromptService(directory.toString()));
    }
}
