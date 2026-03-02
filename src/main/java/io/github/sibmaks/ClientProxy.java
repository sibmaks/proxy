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
import java.util.*;

import static io.github.sibmaks.HopByHopHeaders.HOP_BY_HOP;

public class ClientProxy {

    private static void handleIncomingProxyRequest(HttpExchange ex, HttpClient httpClient, URI relayUri) throws IOException {
        try {
            String method = ex.getRequestMethod();

            // CONNECT не поддерживаем в этом минимальном варианте
            if ("CONNECT".equalsIgnoreCase(method)) {
                ex.sendResponseHeaders(501, -1);
                return;
            }

            URI requestUri = ex.getRequestURI();

            // Для HTTP proxy запросов обычно приходит absolute-form: http://host/path
            // Но некоторые клиенты могут прислать origin-form (/path) + Host header
            String targetUrl;
            if (requestUri.isAbsolute()) {
                targetUrl = requestUri.toString();
            } else {
                String host = firstHeader(ex.getRequestHeaders(), "Host");
                if (host == null || host.isBlank()) {
                    ex.sendResponseHeaders(400, -1);
                    return;
                }
                targetUrl = "http://" + host + requestUri; // requestUri содержит path+query
            }

            Map<String, List<String>> inHeaders = toMap(ex.getRequestHeaders());

            // Убираем hop-by-hop при упаковке (и Connection:*)
            Map<String, List<String>> filteredHeaders = new LinkedHashMap<>();
            for (var e : inHeaders.entrySet()) {
                String name = e.getKey();
                if (name == null) continue;
                String lname = name.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP.contains(lname)) continue;
                filteredHeaders.put(name, e.getValue());
            }

            byte[] body = ex.getRequestBody().readAllBytes();
            byte[] encodedReq = ProxyCodec.encodeRequest(method, targetUrl, filteredHeaders, body);

            HttpRequest relayReq = HttpRequest.newBuilder()
                    .uri(relayUri)
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", ProxyCodec.CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encodedReq))
                    .build();

            HttpResponse<byte[]> relayResp = httpClient.send(relayReq, HttpResponse.BodyHandlers.ofByteArray());

            if (relayResp.statusCode() != 200) {
                // сервер вернул ошибку текстом
                byte[] msg = relayResp.body() == null ? new byte[0] : relayResp.body();
                ex.getResponseHeaders().set("Content-Type", firstOrDefault(relayResp.headers().firstValue("Content-Type"), "text/plain; charset=utf-8"));
                ex.sendResponseHeaders(502, msg.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(msg);
                }
                return;
            }

            ProxyCodec.DecodedResponse decoded = ProxyCodec.decodeResponse(relayResp.body());

            // Отдаём статус/заголовки/тело клиенту
            Headers outHeaders = ex.getResponseHeaders();
            decoded.headers().forEach((k, vlist) -> {
                if (k == null) return;
                String lk = k.toLowerCase(Locale.ROOT);
                if (HOP_BY_HOP.contains(lk)) return;

                // Эти заголовки HttpServer может не любить/сам выставить; оставим как есть, но без дублей Content-Length
                if ("content-length".equals(lk)) return;

                for (String v : vlist) {
                    if (v != null) outHeaders.add(k, v);
                }
            });

            byte[] outBody = decoded.body() == null ? new byte[0] : decoded.body();
            ex.sendResponseHeaders(decoded.statusCode(), outBody.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(outBody);
            }

        } catch (Exception err) {
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            byte[] msg = ("Proxy error: " + err.getClass().getSimpleName() + ": " + err.getMessage())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(502, msg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(msg);
            }
        } finally {
            ex.close();
        }
    }

    private static Map<String, List<String>> toMap(Headers headers) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        headers.forEach((k, v) -> m.put(k, v == null ? List.of() : List.copyOf(v)));
        return m;
    }

    private static String firstHeader(Headers h, String name) {
        List<String> v = h.get(name);
        return (v == null || v.isEmpty()) ? null : v.get(0);
    }

    private static String firstOrDefault(Optional<String> v, String def) {
        return v.orElse(def);
    }

    private static int getIntArg(String[] args, String name, int def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return Integer.parseInt(args[i + 1]);
        }
        return def;
    }

    private static String getStrArg(String[] args, String name, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) return args[i + 1];
        }
        return def;
    }

    public static void main(String[] args) throws Exception {
        // args:
        // --listenPort 8888 --serverHost 127.0.0.1 --serverPort 9000
        int listenPort = getIntArg(args, "--listenPort", 8888);
        String serverHost = getStrArg(args, "--serverHost", "127.0.0.1");
        int serverPort = getIntArg(args, "--serverPort", 9000);

        URI relayUri = URI.create("http://" + serverHost + ":" + serverPort + "/proxy");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        HttpServer proxy = HttpServer.create(new InetSocketAddress(listenPort), 0);
        proxy.createContext("/", ex -> handleIncomingProxyRequest(ex, httpClient, relayUri));
        proxy.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        proxy.start();

        System.out.println("ClientProxy listening on http://127.0.0.1:" + listenPort);
        System.out.println("Forwarding to RemoteProxyServer at " + relayUri);
        System.out.println("Configure your app/browser to use HTTP proxy 127.0.0.1:" + listenPort);
    }
}