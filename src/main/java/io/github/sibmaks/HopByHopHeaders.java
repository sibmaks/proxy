package io.github.sibmaks;

import java.util.Set;

public final class HopByHopHeaders {
    // RFC 7230 hop-by-hop headers (их нельзя пересылать дальше как end-to-end)
    public static final Set<String> HOP_BY_HOP = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
    );

    private HopByHopHeaders() {}
}