package com.fedex.aggregator.router;

import com.fedex.aggregator.service.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class FedexRouter {

    private final FedexHandler fedexHandler;
    private final ExternalApiClient client;

    @Bean
    public RouterFunction<ServerResponse> aggregatorRouter() {
        return RouterFunctions.route()
            .GET("/aggregation", fedexHandler::getAggregatedResponse)
            .build();
    }

}
