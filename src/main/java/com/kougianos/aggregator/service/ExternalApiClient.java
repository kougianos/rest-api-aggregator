package com.kougianos.aggregator.service;

import com.kougianos.aggregator.dto.GenericMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalApiClient {

    private final WebClient webClient;

    public Mono<GenericMap> get(String path, String queryVariables) {
        return webClient
            .get()
            .uri(path + "?q={queryVariables}", queryVariables)
            .retrieve()
            .bodyToMono(GenericMap.class)
            .onErrorResume(e -> {
                log.warn("Error getting response for {}?q={}\nCause: ", path, queryVariables, e);
                return Mono.just(new GenericMap());
            })
            .doOnNext(r -> log.info("Response for {}?q={}: {}", path, queryVariables, r))
            .cache(Duration.ofSeconds(2));
    }

}
