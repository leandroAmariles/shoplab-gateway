package com.shoplab.gateway.support;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Starts the gateway on a real port with every default route pointing to one in-process monolith stub. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractGatewayTest {

    protected static final StubDestination MONOLITH = StubDestination.start("monolith");

    @Value("${local.server.port}")
    protected int port;

    protected WebTestClient client;

    @DynamicPropertySource
    static void monolith(DynamicPropertyRegistry registry) {
        registry.add("shoplab.gateway.monolith-uri", MONOLITH::url);
    }

    @BeforeEach
    void createClient() {
        MONOLITH.reset();
        client = WebTestClient.bindToServer()
                .baseUrl(baseUrl())
                .responseTimeout(Duration.ofSeconds(20))
                .build();
    }

    @AfterEach
    void resetMonolith() {
        MONOLITH.reset();
    }

    protected String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
