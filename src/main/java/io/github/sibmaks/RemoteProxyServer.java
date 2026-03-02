package io.github.sibmaks;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.github.sibmaks.HopByHopHeaders.HOP_BY_HOP;

public class RemoteProxyServer {

    private static void handleProxy(HttpExchange ex, HttpClient httpClient) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }

            String ct = firstHeader(ex.getRequestHeaders(), "Content-Type");
            if (ct == null || !ct.startsWith(ProxyCodec.CONTENT_TYPE)) {
                ex.sendResponseHeaders(415, -1);
                return;
            }

            byte[] payload = ex.getRequestBody().readAllBytes();
            ProxyCodec.DecodedRequest decoded = ProxyCodec.decodeRequest(payload);

            URI target = URI.create(decoded.targetUrl());

            HttpRequest.Builder outReq = HttpRequest.newBuilder()
                    .uri(target)
                    .timeout(Duration.ofSeconds(60));

            byte[] body = decoded.body();
            String method = decoded.method().toUpperCase(Locale.ROOT);

            if (body != null && body.length > 0 && allowsBody(method)) {
                outReq.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            } else {
                outReq.method(method, HttpRequest.BodyPublishers.noBody());
            }

            // Переносим заголовки, убирая hop-by-hop и корректируя Host
            for (var e : decoded.headers().entrySet()) {
                String name = e.getKey();
                if (name == null) continue;
                String lname = name.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP.contains(lname)) continue;

                // Host выставит HttpClient сам, лучше не трогать
                if ("host".equalsIgnoreCase(name)) continue;

                for (String v : e.getValue()) {
                    if (v == null) continue;
                    outReq.header(name, v);
                }
            }

            HttpResponse<byte[]> outResp = httpClient.send(outReq.build(), HttpResponse.BodyHandlers.ofByteArray());

            Map<String, List<String>> respHeaders = new LinkedHashMap<>();
            outResp.headers().map().forEach((k, v) -> {
                if (k == null) return;
                if (HOP_BY_HOP.contains(k.toLowerCase(Locale.ROOT))) return;
                respHeaders.put(k, v);
            });

            byte[] encodedResp = ProxyCodec.encodeResponse(outResp.statusCode(), respHeaders, outResp.body());

            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", ProxyCodec.CONTENT_TYPE);
            ex.sendResponseHeaders(200, encodedResp.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(encodedResp);
            }
        } catch (Exception err) {
            // Возвращаем 502 как proxy error
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            byte[] msg = ("Bad Gateway: " + err.getClass().getSimpleName() + ": " + err.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(502, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
        } finally {
            ex.close();
        }
    }

    private static boolean allowsBody(String method) {
        return !("GET".equals(method) || "HEAD".equals(method) || "TRACE".equals(method));
    }

    private static String firstHeader(Headers h, String name) {
        List<String> v = h.get(name);
        return (v == null || v.isEmpty()) ? null : v.get(0);
    }

    private static int getIntArg(String[] args, String name, int def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return Integer.parseInt(args[i + 1]);
        }
        return def;
    }

    public static void main(String[] args) throws Exception {
        // args: --listenPort 9000
        int listenPort = getIntArg(args, "--listenPort", 9000);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpServer server = HttpServer.create(new InetSocketAddress(listenPort), 0);
        server.createContext("/proxy", ex -> handleProxy(ex, httpClient));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("RemoteProxyServer listening on http://0.0.0.0:" + listenPort + "/proxy");
    }
}