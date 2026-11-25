package com.shoplab.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class SseHeadersAndLatencyTest extends AbstractGatewayTest {

    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration MAXIMUM_ADDED = Duration.ofMillis(200);

    @Test
    void theStreamingHeadersReachTheClientWithoutCompression() {
        MONOLITH.respondWith((request, response) ->
                SseSupport.stream(response, Flux.just("data: " + "x".repeat(4096) + "\n\n")));

        client.post().uri("/api/chat")
                .header("Accept-Encoding", "gzip")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Accel-Buffering", "no")
                .expectHeader().contentTypeCompatibleWith("text/event-stream")
                .expectHeader().doesNotExist("Content-Encoding")
                .expectBody(String.class).value(body -> assertThat(body).startsWith("data: xxxx"));
    }

    /** SC-002: each fragment is delayed by less than 200 ms. */
    @Test
    void eachFragmentIsDelayedVeryLittle() throws InterruptedException {
        Sinks.Many<String> fragments = Sinks.many().unicast().onBackpressureBuffer();
        MONOLITH.respondWith((request, response) -> SseSupport.stream(response, fragments.asFlux()));
        var reading = new SseSupport.Reading(baseUrl() + "/api/chat");
        // The first fragment also pays for connecting and warming up; it is not measured.
        fragments.tryEmitNext("data: warm-up\n\n");
        assertThat(reading.next(WAIT)).isNotNull();

        for (int i = 0; i < 20; i++) {
            long emitted = System.nanoTime();
            fragments.tryEmitNext("data: " + i + "\n\n");
            assertThat(reading.next(WAIT)).isEqualTo("data: " + i + "\n\n");
            assertThat(Duration.ofNanos(System.nanoTime() - emitted)).isLessThan(MAXIMUM_ADDED);
        }
        fragments.tryEmitComplete();
    }
}
