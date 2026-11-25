package com.shoplab.gateway.streaming;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServerResponse;

/** Helpers to emit and read a server-sent event stream one fragment at a time. */
final class SseSupport {

    static final String DONE = "<done>";
    static final String ERROR = "<error>";

    private SseSupport() {
    }

    /** Answers as an event stream, flushing after every fragment. */
    static Publisher<Void> stream(HttpServerResponse response, Flux<String> fragments) {
        return response
                .header("Content-Type", "text/event-stream")
                .header("X-Accel-Buffering", "no")
                .sendGroups(fragments.map(fragment ->
                        Flux.just(Unpooled.copiedBuffer(fragment, StandardCharsets.UTF_8))));
    }

    /** A running subscription to the chat stream: what arrived, and how to cancel it. */
    static final class Reading {

        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final Disposable subscription;

        Reading(String url) {
            this.subscription = HttpClient.create()
                    .post()
                    .uri(url)
                    .responseContent()
                    .asString(StandardCharsets.UTF_8)
                    .subscribe(events::add, error -> events.add(ERROR), () -> events.add(DONE));
        }

        String next(Duration timeout) throws InterruptedException {
            return events.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void cancel() {
            subscription.dispose();
        }
    }
}
