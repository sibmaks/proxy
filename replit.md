# Proxy Chain (Java)

A two-component HTTP proxy chain written in Java.

## Architecture

- **RemoteProxyServer** (`io.github.sibmaks.RemoteProxyServer`) — receives encoded proxy requests and forwards them to the actual target on the internet. Listens on port 9000.
- **ClientProxy** (`io.github.sibmaks.ClientProxy`) — accepts standard HTTP proxy requests from clients/browsers, encodes them using `ProxyCodec`, and forwards to the RemoteProxyServer. Listens on port 8080.
- **ProxyCodec** — binary codec (`application/x-proxychain-v1`) used to serialize/deserialize HTTP requests and responses between the two components.
- **HopByHopHeaders** — utility class for hop-by-hop header filtering.

## Build System

- **Language**: Java 19 (GraalVM 22.3)
- **Build Tool**: Gradle 9 with Gradle wrapper (`./gradlew`)
- No external dependencies (uses Java standard library only)

## Workflows

- **Remote Proxy Server**: `./gradlew runRemoteProxyServer -PlistenPort=9000 --no-daemon`
- **Client Proxy**: `./gradlew runClientProxy -PlistenPort=8080 -PserverHost=localhost -PserverPort=9000 --no-daemon`

## Usage

Configure your browser or application to use `127.0.0.1:8080` as an HTTP proxy. Requests will be forwarded through the RemoteProxyServer at port 9000.

## Notes

- Java toolchain was updated from 21 to 19 to match the installed GraalVM 22.3 runtime.
- Custom Gradle tasks `runClientProxy` and `runRemoteProxyServer` were added to `build.gradle` to support running each component independently.
