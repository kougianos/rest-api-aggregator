package com.fedex.aggregator.router;

import com.fedex.aggregator.dto.*;
import com.fedex.aggregator.service.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FedexHandler {

    // can be moved to app properties
    private static final Map<String, Class<?>> pathToClassMapping = Map.of(
        "pricing", PricingResponse.class,
        "track", TrackResponse.class,
        "shipments", ShipmentsResponse.class);
    private final ExternalApiClient client;

    public Mono<ServerResponse> getAggregatedResponse(ServerRequest request) {
        log.info("REQUEST: {}", request.queryParams().toSingleValueMap());
        Map<String, String> parameters = cleanQueryParameters(request);

        // Create a list to hold all the Mono instances for API calls
        List<Mono<GenericMap>> monoList = new ArrayList<>();

        parameters.forEach((apiName, params) -> {
            List<String> paramList = Arrays.asList(params.split(","));
            // Create a Mono for each API call and add it to the list
            paramList.forEach(p -> {
                Mono<GenericMap> apiCallMono = client.get(apiName, p, GenericMap.class)
                    .defaultIfEmpty(new GenericMap()) // To handle null responses
                    .map(response -> {
                        GenericMap fieldMap = new GenericMap();
                        fieldMap.put(p, response.get(p));
                        return fieldMap;
                    });
                monoList.add(apiCallMono);
            });
        });

        // Combine all Monos in the list into one Mono using Flux.zip
        Mono<List<GenericMap>> combinedMono = Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(GenericMap.class::cast)
            .toList());

        return combinedMono.flatMap(c -> ServerResponse.ok().bodyValue(c));

    }

    private Map<String, String> cleanQueryParameters(ServerRequest request) {
        var map = new HashMap<>(request.queryParams());
        map.keySet().retainAll(pathToClassMapping.keySet());
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }

}
