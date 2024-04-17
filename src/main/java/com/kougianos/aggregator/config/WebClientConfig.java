package com.fedex.aggregator.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final AppProperties appProperties;

    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, appProperties.getExternalApi().getConnectTimeoutMillis())
            .doOnConnected(connection ->
                connection.addHandlerLast(new ReadTimeoutHandler(appProperties.getExternalApi().getReadTimeoutMillis(),
                    TimeUnit.MILLISECONDS)));

        return WebClient.builder()
            .baseUrl(appProperties.getExternalApi().getUrl())
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    }
}
