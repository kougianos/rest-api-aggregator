package com.kougianos.aggregator.router;

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

    @Bean
    public RouterFunction<ServerResponse> aggregatorRouter() {
        return RouterFunctions.route()
            .GET("/aggregation", fedexHandler::getAggregatedResponse)
            .build();
    }

}
