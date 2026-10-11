package com.health.service;

import com.health.controller.DepartmentController;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DepartmentCatalogTest {
    private DepartmentCatalog catalog(String yaml) {
        return new DepartmentCatalog(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void customDictionaryDrivesNormalizationAndPublicResponse() throws Exception {
        DepartmentCatalog catalog = catalog("names: [测试科, 康复科]\naliases: {康复: 康复科}\n");
        assertEquals(List.of("测试科", "康复科"), catalog.names());
        assertEquals("康复科", catalog.normalize(" 康复 "));
        assertNull(catalog.normalize("未配置科室"));
        assertTrue(catalog.mentionedIn("需要康复预约"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.names().add("其他"));
        MockMvcBuilders.standaloneSetup(new DepartmentController(catalog)).build()
                .perform(get("/api/doctor/departments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value("测试科"))
                .andExpect(jsonPath("$.data[1]").value("康复科"));
    }

    @Test
    void invalidDictionaryFailsInsteadOfSilentlyDisagreeingBetweenClients() {
        for (String bad : List.of("names: []", "names: [测试科, 测试科]", "names: [' ']",
                "names: [测试科]\naliases: {测试: 不存在}", "names: [甲, 乙]\naliases: {甲: 乙}")) {
            assertThrows(IllegalArgumentException.class, () -> catalog(bad));
        }
    }
}
