package com.shoplab.gateway.infrastructure.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.gateway.support.AbstractGatewayTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AccessLogFilterTest extends AbstractGatewayTest {

    private String accessLine(CapturedOutput output, String requestId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var line = output.getOut().lines()
                    .filter(candidate -> candidate.contains("access requestId=" + requestId + " "))
                    .findFirst();
            if (line.isPresent()) {
                return line.get();
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("no access line for " + requestId);
    }

    @Test
    void oneLineSaysWhereTheRequestWentWithoutItsContent(CapturedOutput output) throws InterruptedException {
        client.post().uri("/api/knowledge/search?query=mi-direccion-privada")
                .header("X-Request-Id", "log-ok")
                .header("Authorization", "Bearer very-secret")
                .bodyValue("private body")
                .exchange()
                .expectStatus().isOk();

        assertThat(accessLine(output, "log-ok"))
                .contains("routeId=knowledge", "destination=" + MONOLITH.url(), "method=POST", "status=200",
                        "durationMs=");
        assertThat(output.getOut().lines().filter(line -> line.contains("access requestId=log-ok ")))
                .hasSize(1);
        assertThat(output.getOut())
                .doesNotContain("mi-direccion-privada", "very-secret", "private body", "/api/knowledge");
    }

    @Test
    void aGatewayErrorIsLoggedWithItsStatus(CapturedOutput output) throws InterruptedException {
        client.get().uri("/api/catalog").header("X-Request-Id", "log-404").exchange().expectStatus().isNotFound();

        assertThat(accessLine(output, "log-404")).contains("routeId=-", "destination=-", "status=404");
    }

    @Test
    void theProbesAreNotLogged(CapturedOutput output) {
        client.get().uri("/actuator/health").header("X-Request-Id", "log-probe").exchange().expectStatus().isOk();

        assertThat(output.getOut()).doesNotContain("access requestId=log-probe");
    }
}
