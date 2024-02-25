package com.fedex.aggregator.router;

import com.fedex.aggregator.dto.*;
import com.fedex.aggregator.service.ExternalApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.fedex.aggregator.dto.Constants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FedexHandler {

    // can be moved to app properties
    private static final List<String> acceptableParameters = List.of(PRICING, TRACK, SHIPMENTS);
    private final ExternalApiClient client;

    public Mono<ServerResponse> getAggregatedResponse(ServerRequest request) {
        log.info("REQUEST: {}", request.queryParams().toSingleValueMap());
        Map<String, String> parameters = cleanQueryParameters(request);

        List<Mono<Entry<String, GenericMap>>> monoList = new ArrayList<>();

        parameters.forEach((apiName, params) -> {
            List<String> paramList = Arrays.asList(params.split(","));
            paramList.forEach(p -> {
                var apiCallMono = client.get(apiName, p, GenericMap.class)
                    .defaultIfEmpty(new GenericMap())
                    .map(response -> {
                        GenericMap fieldMap = new GenericMap();
                        fieldMap.put(p, response.get(p));
                        return Map.entry(apiName, fieldMap);
                    });
                monoList.add(apiCallMono);
            });
        });

        Mono<List<Entry<String, GenericMap>>> combinedMono = Mono.zip(monoList, objects -> Arrays.stream(objects)
            .map(obj -> (Entry<String, GenericMap>) obj)
            .toList());

        var serverResponse = combinedMono.map(responseList -> {
            Map<String, GenericMap> aggregatedResponse = new HashMap<>();
            aggregatedResponse.put(TRACK, new GenericMap());
            aggregatedResponse.put(SHIPMENTS, new GenericMap());
            aggregatedResponse.put(PRICING, new GenericMap());
            responseList.forEach(responseEntry -> {
                var apiResponse = responseEntry.getValue();

                aggregatedResponse.get(String.valueOf(responseEntry.getKey()))
                    .put(apiResponse.firstEntry().getKey(), apiResponse.firstEntry().getValue());

            });
            List<String> keysToRemove = new ArrayList<>();
            aggregatedResponse.forEach((key, value) -> {
                if (value.isEmpty()) {
                    keysToRemove.add(key);
                }
            });
            keysToRemove.forEach(aggregatedResponse::remove);
            return aggregatedResponse;
        });

        return serverResponse.flatMap(resp -> {
            log.info("RESPONSE: {}\n", resp);
            return ServerResponse.ok().bodyValue(resp);
        });

    }

    private Map<String, String> cleanQueryParameters(ServerRequest request) {
        var map = new HashMap<>(request.queryParams());
        map.keySet().retainAll(acceptableParameters);
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().get(0)));
    }

}
