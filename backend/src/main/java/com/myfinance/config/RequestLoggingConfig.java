package com.myfinance.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs all incoming HTTP requests and their response status.
 * Log file: ./logs/myfinance.log
 */
@Configuration
@Slf4j
public class RequestLoggingConfig {

    @Bean
    public OncePerRequestFilter requestLoggingFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                long start = System.currentTimeMillis();
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    long duration = System.currentTimeMillis() - start;
                    String method = request.getMethod();
                    String uri = request.getRequestURI();
                    int status = response.getStatus();

                    if (!uri.startsWith("/h2-console") && !uri.contains("/swagger") && !uri.contains("/v3/api-docs")) {
                        if (status >= 400) {
                            log.warn("[HTTP] {} {} → {} ({}ms)", method, uri, status, duration);
                        } else {
                            log.info("[HTTP] {} {} → {} ({}ms)", method, uri, status, duration);
                        }
                    }
                }
            }
        };
    }
}
