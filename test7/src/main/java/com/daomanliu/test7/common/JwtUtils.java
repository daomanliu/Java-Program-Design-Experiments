package com.daomanliu.test7.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.List;

public class JwtUtils {
    private static final Key KEY = Keys.hmacShaKeyFor("daomanliu-test6-jwt-secret-key-2026".getBytes());
    private static final long EXPIRE = 120 * 1000;

    public static String generate(String username, List<String> roles) {
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE))
                .signWith(KEY)
                .compact();
    }

    private static Claims parse(String token) {
        return Jwts.parserBuilder().setSigningKey(KEY).build().parseClaimsJws(token).getBody();
    }

    public static boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public static String getUsername(String token) {
        return parse(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getRoles(String token) {
        return parse(token).get("roles", List.class);
    }
}
