package com.fedex.aggregator.router;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Service
public class ServiceHandler {

    public Mono<ServerResponse> updateItems(ServerRequest request) {

        return ServerResponse.ok().bodyValue("hello");
    }

}
