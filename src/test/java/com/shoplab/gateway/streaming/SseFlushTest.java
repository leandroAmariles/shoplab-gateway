package com.shoplab.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class SseFlushTest extends AbstractGatewayTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void eachFragmentArrivesBeforeTheNextOneIsEmitted() throws InterruptedException {
        Sinks.Many<String> fragments = Sinks.many().unicast().onBackpressureBuffer();
        MONOLITH.respondWith((request, response) -> SseSupport.stream(response, fragments.asFlux()));

        var reading = new SseSupport.Reading(baseUrl() + "/api/chat");

        fragments.tryEmitNext("data: one\n\n");
        assertThat(reading.next(WAIT)).isEqualTo("data: one\n\n");
        fragments.tryEmitNext("data: two\n\n");
        assertThat(reading.next(WAIT)).isEqualTo("data: two\n\n");
        fragments.tryEmitComplete();
        assertThat(reading.next(WAIT)).isEqualTo(SseSupport.DONE);
    }
}
