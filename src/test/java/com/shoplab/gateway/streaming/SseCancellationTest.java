package com.shoplab.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class SseCancellationTest extends AbstractGatewayTest {

    @Test
    void cancellingInTheClientClosesTheConnectionToTheDestination() throws InterruptedException {
        CountDownLatch closed = new CountDownLatch(1);
        MONOLITH.respondWith((request, response) -> {
            response.withConnection(connection -> connection.onDispose(closed::countDown));
            return SseSupport.stream(response, Flux.interval(Duration.ofMillis(50))
                    .map(tick -> "data: " + tick + "\n\n")
                    .doOnCancel(closed::countDown));
        });

        var reading = new SseSupport.Reading(baseUrl() + "/api/chat");
        assertThat(reading.next(Duration.ofSeconds(5))).startsWith("data: ");
        reading.cancel();

        assertThat(closed.await(5, TimeUnit.SECONDS)).as("the destination saw the cancellation").isTrue();
    }
}
