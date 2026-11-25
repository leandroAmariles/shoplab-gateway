package com.shoplab.gateway.support;

import io.netty.handler.codec.http.HttpHeaders;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;
import reactor.netty.resources.LoopResources;

/**
 * In-process stand-in for a destination service. By default it records each request and answers {@code 200} with its
 * own name; a test can replace the behaviour to control timing, status, headers and streaming.
 */
public final class StubDestination implements AutoCloseable {

    /** What the destination received. */
    public record Received(String method, String uri, HttpHeaders headers, byte[] body) {
    }

    private final String name;
    // Its own event loop: closing a gateway context disposes the shared one of Reactor Netty.
    private final LoopResources loop = LoopResources.create("stub");
    private final DisposableServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private volatile BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> behaviour;

    private StubDestination(String name) {
        this.name = name;
        this.behaviour = this::recordAndAnswer;
        this.server = HttpServer.create()
                .runOn(loop)
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> behaviour.apply(request, response))
                .bindNow(Duration.ofSeconds(10));
    }

    public static StubDestination start(String name) {
        return new StubDestination(name);
    }

    public String url() {
        return "http://127.0.0.1:" + server.port();
    }

    public List<Received> received() {
        return received;
    }

    public void respondWith(BiFunction<HttpServerRequest, HttpServerResponse, Publisher<Void>> newBehaviour) {
        this.behaviour = newBehaviour;
    }

    /** Back to the default behaviour, forgetting what was received. */
    public void reset() {
        this.behaviour = this::recordAndAnswer;
        received.clear();
    }

    private Publisher<Void> recordAndAnswer(HttpServerRequest request, HttpServerResponse response) {
        return request.receive()
                .aggregate()
                .asByteArray()
                .defaultIfEmpty(new byte[0])
                .flatMap(body -> {
                    received.add(new Received(request.method().name(), request.uri(),
                            request.requestHeaders().copy(), body));
                    return response.header("Content-Type", "text/plain")
                            .sendString(Mono.just(name))
                            .then();
                });
    }

    @Override
    public void close() {
        server.disposeNow(Duration.ofSeconds(10));
        loop.dispose();
    }
}
