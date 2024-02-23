package com.fedex.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalApiClient {

    private final WebClient webClient;

    public <T> Mono<T> get(String path, Class<T> clazz) {
        return webClient
            .get()
            .uri(path)
            .retrieve()
            .bodyToMono(clazz)
            .onErrorResume(e -> {
                log.warn("Error getting response for {}\nCause: ", path, e);
                return Mono.empty();
            })
            .doOnNext(r -> log.info("Response for {}: {}", path, r));
    }

}
