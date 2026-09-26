package studio.aakar.api.support;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Integration tests start from an empty schema: clean, then migrate (no Docker here, so no Testcontainers). */
@TestConfiguration(proxyBeanMethods = false)
public class TestFlywayConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
