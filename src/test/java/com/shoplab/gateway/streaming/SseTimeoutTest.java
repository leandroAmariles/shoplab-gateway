package com.shoplab.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The response timeout limits the wait for the headers, not the length of a stream already started. */
@TestPropertySource(properties = "shoplab.gateway.routes.chat.response-timeout=500ms")
class SseTimeoutTest extends AbstractGatewayTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void aStreamLongerThanTheTimeoutIsNotCut() throws InterruptedException {
        MONOLITH.respondWith((request, response) -> SseSupport.stream(response, Flux.concat(
                Mono.just("data: first\n\n"),
                Mono.just("data: late\n\n").delayElement(Duration.ofMillis(1500)))));

        var reading = new SseSupport.Reading(baseUrl() + "/api/chat");

        assertThat(reading.next(WAIT)).isEqualTo("data: first\n\n");
        assertThat(reading.next(WAIT)).isEqualTo("data: late\n\n");
        assertThat(reading.next(WAIT)).isEqualTo(SseSupport.DONE);
    }
}
