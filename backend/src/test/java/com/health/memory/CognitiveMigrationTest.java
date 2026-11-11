package com.health.memory;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CognitiveMigrationTest {
    @Test void v11PreservesExistingFactsAndCreatesTheirInitialEvidenceVersion() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:legacy-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");jdbc.update("INSERT INTO sys_user VALUES(1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V9__add_user_long_term_memory.sql")).execute(ds);
        jdbc.update("INSERT INTO user_memory(user_id,fact_key,category,content,source_text) VALUES(1,'health.allergy.peanut','health','花生过敏','我对花生过敏')");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__cognitive_memory.sql")).execute(ds);
        var repository=new CognitiveMemoryRepository(jdbc);var fact=repository.fact(1L,"health.allergy.peanut");
        assertEquals("花生过敏",fact.content());assertEquals(1,fact.version());assertEquals(.85,fact.importance(),.0001);
        var versions=repository.versions(1L,fact.id());assertEquals(1,versions.size());assertEquals("我对花生过敏",versions.get(0).sourceText());
    }
}
