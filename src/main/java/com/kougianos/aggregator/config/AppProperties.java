package com.kougianos.aggregator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private ExternalApi externalApi;
    private boolean enableQueueScheduler;

    @Data
    public static class ExternalApi {
        private String url;
        private int readTimeoutMillis;
        private int connectTimeoutMillis;
    }

}
