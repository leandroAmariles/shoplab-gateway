package com.shoplab.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Mono;

class UploadTest extends AbstractGatewayTest {

    private static final int TEN_MIB = 10 * 1024 * 1024;

    @Test
    void aDocumentOfTheLargestSizeArrivesWhole() {
        byte[] document = new byte[TEN_MIB];
        document[TEN_MIB - 1] = 7;

        client.post().uri("/api/documents")
                .header("Content-Type", "multipart/form-data; boundary=x")
                .bodyValue(document)
                .exchange()
                .expectStatus().isOk();

        byte[] received = MONOLITH.received().getFirst().body();
        assertThat(received).hasSize(TEN_MIB);
        assertThat(received[TEN_MIB - 1]).isEqualTo((byte) 7);
    }

    /** The backend rejects an oversized upload before reading it; its typed error must still reach the client. */
    @RepeatedTest(5)
    void theErrorOfADestinationThatRejectsWithoutReadingReachesTheClient() {
        String error = "{\"code\":\"SLHD_RETR_00002\",\"message\":\"The document is too large\"}";
        MONOLITH.respondWith((request, response) -> response.status(413)
                .header("Content-Type", "application/json")
                .sendString(Mono.just(error)));

        client.post().uri("/api/documents")
                .header("Content-Type", "multipart/form-data; boundary=x")
                .bodyValue(new byte[TEN_MIB + 1024 * 1024])
                .exchange()
                .expectStatus().isEqualTo(413)
                .expectBody(String.class).isEqualTo(error);
    }
}
