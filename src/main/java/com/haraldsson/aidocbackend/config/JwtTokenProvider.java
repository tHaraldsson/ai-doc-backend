package com.haraldsson.aidocbackend.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {

        try {

            if (jwtSecret == null || jwtSecret.length() < 32) {
                System.out.println("JWT secret is too short, generating secure key...");
                return Keys.secretKeyFor(SignatureAlgorithm.HS256);

            }
            String base64Key = Base64.getEncoder().encodeToString(jwtSecret.getBytes());
            byte[] decodedKey = Base64.getDecoder().decode(base64Key);
            return Keys.hmacShaKeyFor(decodedKey);
        } catch (Exception e) {
            System.err.println("error creating JWT signing key: " + e.getMessage());
            return Keys.secretKeyFor(SignatureAlgorithm.HS256);
        }
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);


        String token = Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
        System.out.println("JWT Token generated successfully for user: " + username);
        return token;
    }

    public String getUsernameFromToken(String token) {

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            System.err.println("error getting username from token: " + e.getMessage());
            throw new RuntimeException("Invalid JWT token" + e);
        }
    }

    public boolean validateToken(String token) {

        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            System.err.println("JWT Validation error: " + e.getMessage());
            return false;
        }
    }
}
