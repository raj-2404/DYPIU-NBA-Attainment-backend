package com.dypiu.nba.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.threads.virtual.enabled=true",
        "server.compression.enabled=true",
        "spring.datasource.hikari.maximum-pool-size=15",
        "spring.datasource.hikari.minimum-idle=5",
        "spring.datasource.hikari.leak-detection-threshold=60000",
        "spring.jpa.properties.hibernate.jdbc.batch_size=50",
        "spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true"
})
public class ProductionConfigurationProfileTest {

    @Value("${spring.threads.virtual.enabled}")
    private boolean virtualThreadsEnabled;

    @Value("${server.compression.enabled}")
    private boolean compressionEnabled;

    @Value("${spring.datasource.hikari.maximum-pool-size}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle}")
    private int minIdle;

    @Value("${spring.datasource.hikari.leak-detection-threshold}")
    private long leakDetectionThreshold;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size}")
    private int batchSize;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_versioned_data}")
    private boolean batchVersionedData;

    @Test
    @DisplayName("Verify Production Performance and Pool Configurations")
    void testProductionPerformanceProperties() {
        assertTrue(virtualThreadsEnabled, "Virtual threads must be enabled");
        assertTrue(compressionEnabled, "GZIP compression must be enabled");
        assertEquals(15, maxPoolSize, "Default max pool size should be 15");
        assertEquals(5, minIdle, "Default min idle should be 5");
        assertEquals(60000, leakDetectionThreshold, "Leak detection threshold should be 60000ms");
        assertEquals(50, batchSize, "Hibernate JDBC batch size should be 50");
        assertTrue(batchVersionedData, "Hibernate batch versioned data should be enabled");
    }
}
