package com.fedex.aggregator.router;

import com.fedex.aggregator.dto.TrackResponse;
import com.fedex.aggregator.service.ExternalApiClient;
import lombok.RequiredArgsConstructor;
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
public class FedexRouter {

    private final FedexHandler fedexHandler;
    private final ExternalApiClient client;

    @Bean
    RouterFunction<ServerResponse> get() {
        return RouterFunctions.route(GET("/"), fedexHandler::updateItems);
    }

    @Bean
    RouterFunction<ServerResponse> test() {
        return RouterFunctions.route(GET("/{path}"), this::testFunctionality);
    }

    private Mono<ServerResponse> testFunctionality(ServerRequest request) {
        var path = request.pathVariable("path");
        return client.get("/track?q=109347263,123456891", TrackResponse.class)
            .flatMap(response -> ServerResponse.ok()
                .contentType(APPLICATION_JSON)
                .bodyValue(response));
    }

}
