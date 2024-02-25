package com.fedex.aggregator.router;

import com.fedex.aggregator.dto.GenericMap;
import com.fedex.aggregator.dto.PricingResponse;
import com.fedex.aggregator.dto.ShipmentsResponse;
import com.fedex.aggregator.dto.TrackResponse;
import com.fedex.aggregator.service.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
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

        List<Mono<GenericMap>> monoList = new ArrayList<>();

        parameters.forEach((apiName, params) -> {
            List<String> paramList = Arrays.asList(params.split(","));
            paramList.forEach(p -> {
                Mono<GenericMap> apiCallMono = client.get(apiName, p, GenericMap.class)
                    .defaultIfEmpty(new GenericMap())
                    .map(response -> {
                        GenericMap fieldMap = new GenericMap();
                        fieldMap.put(p, response.get(p));
                        return fieldMap;
                    });
                monoList.add(apiCallMono);
            });
        });

        Mono<List<GenericMap>> combinedMono = Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(GenericMap.class::cast)
            .toList());

        var serverResponse = combinedMono.map(responseList -> {
            Map<String, GenericMap> aggregatedResponse = new HashMap<>();
            aggregatedResponse.put("track", new GenericMap());
            aggregatedResponse.put("shipments", new GenericMap());
            aggregatedResponse.put("pricing", new GenericMap());
            responseList.forEach(response -> {
                var entry = response.firstEntry();
                if (entry.getValue() instanceof String) {
                    aggregatedResponse.get("track").put(entry.getKey(), entry.getValue());
                } else if (entry.getValue() instanceof List) {
                    aggregatedResponse.get("shipments").put(entry.getKey(), entry.getValue());
                } else if (entry.getValue() instanceof Number) {
                    aggregatedResponse.get("pricing").put(entry.getKey(), entry.getValue());
                }
            });
            return aggregatedResponse;
        });

        return serverResponse.flatMap(resp -> {
            log.info("RESPONSE: {}\n", resp);
            return ServerResponse.ok().bodyValue(resp);
        });

    }

    private Map<String, String> cleanQueryParameters(ServerRequest request) {
        var map = new HashMap<>(request.queryParams());
        map.keySet().retainAll(pathToClassMapping.keySet());
        return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
    }

}
