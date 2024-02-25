package com.fedex.aggregator.router;

import com.fedex.aggregator.dto.GenericMap;
import com.fedex.aggregator.service.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

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

    @Bean
    RouterFunction<ServerResponse> test() {
        return RouterFunctions.route(GET("/{path}"), this::testFunctionality);
    }

    private Mono<ServerResponse> testFunctionality(ServerRequest request) {
        var path = request.pathVariable("path");
        return client.get("/track", "109347263,123456891", GenericMap.class)
            .flatMap(response -> ServerResponse.ok()
                .contentType(APPLICATION_JSON)
                .bodyValue(response));
    }

}
