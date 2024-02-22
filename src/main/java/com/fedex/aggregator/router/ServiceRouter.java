package com.fedex.aggregator.router;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;

@Configuration
@RequiredArgsConstructor
public class ServiceRouter {

    private final ServiceHandler serviceHandler;

    @Bean
    RouterFunction<ServerResponse> create() {
        return RouterFunctions
            .route(GET("/"), serviceHandler::updateItems);
    }

}
