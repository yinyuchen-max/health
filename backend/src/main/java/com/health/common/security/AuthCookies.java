package com.health.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Host-only, HttpOnly cookies. Browser reads the masked CSRF token via /csrf. */
@Component
public class AuthCookies implements CsrfTokenRepository {
    public static final String REFRESH = "health_refresh", CSRF = "health_csrf";
    @Value("${jwt.cookie-secure:true}") private boolean secure;
    public String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if (name.equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public void writeRefresh(HttpServletResponse response, String value, long expiresAt) {
        write(response, REFRESH, value, Duration.ofMillis(Math.max(0, expiresAt - System.currentTimeMillis())));
    }
    public void clearRefresh(HttpServletResponse response) { write(response, REFRESH, "", Duration.ZERO); }
    private void write(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(name, value).httpOnly(true)
                .secure(secure).sameSite("Strict").path("/api/user");
        if (maxAge != null) cookie.maxAge(maxAge);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
    @Override public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", TokenDigests.randomToken());
    }
    @Override public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        write(response, CSRF, token == null ? "" : token.getToken(), token == null ? Duration.ZERO : null);
    }
    @Override public CsrfToken loadToken(HttpServletRequest request) {
        String token = read(request, CSRF);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", token);
    }
}
