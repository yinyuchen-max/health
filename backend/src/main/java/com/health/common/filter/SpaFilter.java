package com.health.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SpaFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());

        if (isSpaRoute(path)) {
            HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request) {
                @Override
                public String getRequestURI() {
                    return request.getContextPath() + "/index.html";
                }

                @Override
                public String getServletPath() {
                    return "/index.html";
                }

                @Override
                public String getPathInfo() {
                    return null;
                }
            };
            chain.doFilter(wrapper, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isSpaRoute(String path) {
        return !path.startsWith("/api") && !path.startsWith("/static") && !path.startsWith("/assets")
                && !path.contains(".") && !"/".equals(path) && !"/index.html".equals(path);
    }
}
