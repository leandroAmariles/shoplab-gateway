package com.shoplab.gateway.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Sends a request line exactly as written: HTTP clients normalise dot segments before sending them. */
public final class RawHttp {

    private RawHttp() {
    }

    /** The whole response of {@code GET <target>}, status line and body included. */
    public static String get(int port, String target) throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(10_000);
            String request = "GET " + target + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
