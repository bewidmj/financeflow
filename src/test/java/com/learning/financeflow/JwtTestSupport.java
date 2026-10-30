package com.learning.financeflow;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

public final class JwtTestSupport {

    public static final String KEY_ID = "financeflow-test-key";
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    private JwtTestSupport() {
    }

    public static String jwksUri() {
        return "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort()
                + "/.well-known/jwks.json";
    }

    public static KeyPair keyPair() {
        return KEY_PAIR;
    }

    public static String token(UUID subject) {
        return token(subject, "USER");
    }

    public static String token(UUID subject, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(KEY_ID)
                .and()
                .subject(subject.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .claim("email", "test@example.com")
                .claim("role", role)
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public static String token(
            UUID subject,
            KeyPair pair,
            String keyId,
            io.jsonwebtoken.security.SecureDigestAlgorithm<
                    java.security.PrivateKey, ?> algorithm,
            Instant issuedAt,
            Instant expiration) {
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject(subject.toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration))
                .claim("email", "test@example.com")
                .claim("role", "USER")
                .signWith(pair.getPrivate(), algorithm)
                .compact();
    }

    public static String hs384Token(UUID subject) {
        byte[] secret = new byte[48];
        java.util.Arrays.fill(secret, (byte) 0x42);
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(KEY_ID)
                .and()
                .subject(subject.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(secret), Jwts.SIG.HS384)
                .compact();
    }

    public static String tokenWithoutKeyId(UUID subject) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public static String unsignedToken(UUID subject) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"kid\":\"financeflow-test-key\"}"
                        .getBytes(StandardCharsets.UTF_8));
        String claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + subject + "\",\"exp\":"
                        + Instant.now().plusSeconds(300).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));
        return header + "." + claims + ".";
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is not available for tests", ex);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> {
                byte[] body = jwks().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set(
                        "Content-Type", "application/jwk-set+json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start the test JWKS endpoint", ex);
        }
    }

    private static String jwks() {
        var publicKey = (java.security.interfaces.RSAPublicKey) KEY_PAIR.getPublic();
        return """
                {"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"%s"}]}
                """.formatted(
                KEY_ID,
                base64UrlUnsigned(publicKey.getModulus()),
                base64UrlUnsigned(publicKey.getPublicExponent())).trim();
    }

    private static String base64UrlUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        int offset = bytes.length > 1 && bytes[0] == 0 ? 1 : 0;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
    }
}
