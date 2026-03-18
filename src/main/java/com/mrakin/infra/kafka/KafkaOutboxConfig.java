package com.mrakin.infra.kafka;

import com.mrakin.domain.event.UrlAccessedEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class KafkaOutboxConfig {

    @Bean
    NewTopic urlAccessedTopic() {
        return TopicBuilder.name("url-accessed")
                .partitions(10)
                .replicas(1)
                .build();
    }

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(EventExternalizationConfiguration.annotatedAsExternalized())
                .route(
                    UrlAccessedEvent.class,
                    it -> RoutingTarget.forTarget("url-accessed").andKey(extractDomain(it.url().getOriginalUrl()))
                )
                .build();
    }

    private String extractDomain(String url) {
        if (url == null) return "";
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return (host != null) ? host : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }
}
