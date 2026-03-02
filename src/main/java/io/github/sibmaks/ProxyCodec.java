package io.github.sibmaks;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProxyCodec {

    public static final String CONTENT_TYPE = "application/x-proxychain-v1";

    public static byte[] encodeRequest(String method, String targetUrl, Map<String, List<String>> headers, byte[] body) throws IOException {
        Objects.requireNonNull(method);
        Objects.requireNonNull(targetUrl);
        if (headers == null) headers = Map.of();
        if (body == null) body = new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        writeString(out, method);
        writeString(out, targetUrl);

        // headers as repeated (name,value) pairs
        int pairs = countHeaderPairs(headers);
        out.writeInt(pairs);
        for (var e : headers.entrySet()) {
            String name = e.getKey();
            if (name == null) continue;
            for (String value : e.getValue()) {
                if (value == null) value = "";
                writeString(out, name);
                writeString(out, value);
            }
        }

        out.writeInt(body.length);
        out.write(body);

        out.flush();
        return baos.toByteArray();
    }

    public static DecodedRequest decodeRequest(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        String method = readString(in);
        String targetUrl = readString(in);

        int pairs = in.readInt();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String name = readString(in);
            String value = readString(in);
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        int bodyLen = in.readInt();
        byte[] body = in.readNBytes(bodyLen);

        return new DecodedRequest(method, targetUrl, headers, body);
    }

    public static byte[] encodeResponse(int statusCode, Map<String, List<String>> headers, byte[] body) throws IOException {
        if (headers == null) headers = Map.of();
        if (body == null) body = new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(statusCode);

        int pairs = countHeaderPairs(headers);
        out.writeInt(pairs);
        for (var e : headers.entrySet()) {
            String name = e.getKey();
            if (name == null) continue;
            for (String value : e.getValue()) {
                if (value == null) value = "";
                writeString(out, name);
                writeString(out, value);
            }
        }

        out.writeInt(body.length);
        out.write(body);

        out.flush();
        return baos.toByteArray();
    }

    public static DecodedResponse decodeResponse(byte[] bytes) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int status = in.readInt();

        int pairs = in.readInt();
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String name = readString(in);
            String value = readString(in);
            headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        int bodyLen = in.readInt();
        byte[] body = in.readNBytes(bodyLen);

        return new DecodedResponse(status, headers, body);
    }

    private static int countHeaderPairs(Map<String, List<String>> headers) {
        int n = 0;
        for (var e : headers.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            n += e.getValue().size();
        }
        return n;
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] b = in.readNBytes(len);
        return new String(b, StandardCharsets.UTF_8);
    }

    public record DecodedRequest(String method, String targetUrl, Map<String, List<String>> headers, byte[] body) {}
    public record DecodedResponse(int statusCode, Map<String, List<String>> headers, byte[] body) {}

    private ProxyCodec() {}
}