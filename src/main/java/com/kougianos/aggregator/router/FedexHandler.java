package com.kougianos.aggregator.router;

import com.kougianos.aggregator.service.AggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static com.kougianos.aggregator.dto.Constants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class FedexHandler {

    // can be moved to app properties
    private static final List<String> ACCEPTABLE_PARAMETERS = List.of(PRICING, TRACK, SHIPMENTS);
    private final AggregationService aggregationService;

    public Mono<ServerResponse> getAggregatedResponse(ServerRequest request) {
        var requestId = RandomStringUtils.randomAlphabetic(5);
        long startTime = System.currentTimeMillis();
        log.info("REQUEST {}: {}", requestId, request.queryParams().toSingleValueMap());
        Map<String, String> parameters = cleanQueryParameters(request);

        var serverResponse = aggregationService.getAggregatedResponse(parameters);

        return serverResponse.flatMap(resp -> {
            long endTime = System.currentTimeMillis();
            log.info("RESPONSE {} ({}ms): {}\n", requestId, endTime - startTime, resp);
            return ServerResponse.ok().bodyValue(resp);
        });

    }

    private Map<String, String> cleanQueryParameters(ServerRequest request) {
        var map = new HashMap<>(request.queryParams());
        map.keySet().retainAll(ACCEPTABLE_PARAMETERS);
        return map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getFirst()));
    }

}
