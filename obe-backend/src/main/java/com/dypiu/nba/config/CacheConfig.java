package com.dypiu.nba.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_DEPARTMENTS = "departments";
    public static final String CACHE_MASTER_PROGRAMMES = "masterProgrammes";
    public static final String CACHE_PROGRAMME_BATCHES = "programmeBatches";
    public static final String CACHE_COURSES = "courses";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                CACHE_DEPARTMENTS,
                CACHE_MASTER_PROGRAMMES,
                CACHE_PROGRAMME_BATCHES,
                CACHE_COURSES
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats());
        return cacheManager;
    }
}
