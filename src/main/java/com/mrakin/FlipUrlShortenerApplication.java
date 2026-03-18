package com.mrakin;

import com.mrakin.usecases.generator.ShortCodeGenerator;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Map;

@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "1m")
public class FlipUrlShortenerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlipUrlShortenerApplication.class, args);
    }

    @Bean
    @Primary
    public ShortCodeGenerator selectedShortCodeGenerator(
            Map<String, ShortCodeGenerator> generators,
            @Value("${app.generator.name:sha256Generator}") String generatorName) {
        ShortCodeGenerator generator = generators.get(generatorName);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown generator: " + generatorName);
        }
        return generator;
    }

    /**
     * Dedicated scheduler for background tasks with limited parallelism.
     * Prevents maintenance tasks from hanging and blocking each other indefinitely.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "cleanupExecutor")
    public ThreadPoolTaskExecutor cleanupExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("cleanup-worker-");
        executor.initialize();
        return executor;
    }
}
