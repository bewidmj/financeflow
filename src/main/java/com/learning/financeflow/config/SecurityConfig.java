package com.learning.financeflow.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${JWT_JWKS_URI}") String jwksUri) {
        URI uri;
        try {
            uri = URI.create(jwksUri);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "JWT_JWKS_URI must be a valid HTTP(S) URI", ex);
        }
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "JWT_JWKS_URI must be a valid HTTP(S) URI without user information");
        }

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestTemplate restOperations = new RestTemplate(requestFactory);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(restOperations)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                jwt -> jwt.getHeaders().get("kid") instanceof String keyId
                        && !keyId.isBlank()
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token", "JWT kid header is required", null))));
        return decoder;
    }
}
