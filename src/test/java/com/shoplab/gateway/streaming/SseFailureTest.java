package com.shoplab.gateway.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class SseFailureTest extends AbstractGatewayTest {

    private static final Duration WAIT = Duration.ofSeconds(5);

    @Test
    void aStreamCutByTheDestinationIsNeverClosedAsComplete() throws InterruptedException {
        Sinks.Empty<Void> cut = Sinks.empty();
        MONOLITH.respondWith((request, response) -> {
            response.withConnection(connection -> cut.asMono()
                    .doOnSuccess(ignored -> connection.channel().close())
                    .subscribe());
            return SseSupport.stream(response, Flux.concat(Flux.just("data: partial\n\n"), Flux.never()));
        });

        var reading = new SseSupport.Reading(baseUrl() + "/api/chat");
        assertThat(reading.next(WAIT)).isEqualTo("data: partial\n\n");
        cut.tryEmitEmpty();

        assertThat(reading.next(WAIT)).isEqualTo(SseSupport.ERROR);
    }

    @Test
    void theErrorEventOfTheDestinationPassesThrough() throws InterruptedException {
        String errorEvent = "event: error\ndata: {\"code\":\"SLHD_CHAT_00002\"}\n\n";
        MONOLITH.respondWith((request, response) -> SseSupport.stream(response, Flux.just(errorEvent)));

        var reading = new SseSupport.Reading(baseUrl() + "/api/chat");

        assertThat(reading.next(WAIT)).isEqualTo(errorEvent);
        assertThat(reading.next(WAIT)).isEqualTo(SseSupport.DONE);
    }
}
