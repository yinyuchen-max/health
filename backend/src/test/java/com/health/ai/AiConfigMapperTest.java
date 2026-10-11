package com.health.ai;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import static org.junit.jupiter.api.Assertions.*;
class AiConfigMapperTest {
    @Test void migrationAndRealMapperRoundTripWithoutStoringPlaintext() throws Exception {
        JdbcDataSource ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:ai-config;MODE=MySQL;DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V8__add_user_ai_config.sql")).execute(ds);
        MybatisConfiguration config=new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true); config.addMapper(AiConfigMapper.class);
        MybatisSqlSessionFactoryBean factory=new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(config);
        SqlSessionFactory sessions=factory.getObject();
        try(var session=sessions.openSession(true)) {
            var mapper=session.getMapper(AiConfigMapper.class);
            mapper.save(new AiConfigMapper.StoredConfig(1L,"deepseek","m1","encrypted",null,"r1"));
            assertEquals("encrypted",mapper.find(1L).encryptedApiKey()); assertEquals("r1",mapper.find(1L).revision());
            mapper.save(new AiConfigMapper.StoredConfig(1L,"dashscope","m2","new-encrypted","embedding-encrypted","r2"));
            assertEquals("m2",mapper.find(1L).model()); assertEquals("embedding-encrypted",mapper.find(1L).encryptedEmbeddingApiKey());
            assertNull(mapper.find(2L)); mapper.delete(2L); assertNotNull(mapper.find(1L)); mapper.delete(1L); assertNull(mapper.find(1L));
        }
    }
}
