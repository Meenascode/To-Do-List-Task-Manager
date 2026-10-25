package com.example.todo.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import com.example.todo.security.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Session-based security: form login handled by Spring Security (BCrypt), CSRF protection ON,
 * everything except the login page and static assets requires authentication.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint apiEntryPoint = (req, res, ex) ->
                writeJson(res, mapper, 401, "Please log in to continue.");

        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entryPoints = new LinkedHashMap<>();
        entryPoints.put(new AntPathRequestMatcher("/api/**"), apiEntryPoint);
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entryPoints);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login.html"));

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/favicon.svg", "/error",
                        "/css/**", "/vendor/**", "/js/auth.js", "/js/theme-init.js",
                        "/api/auth/csrf", "/api/auth/register").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/api/auth/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler((req, res, auth) -> {
                    AuthUser user = (AuthUser) auth.getPrincipal();
                    writeJson(res, mapper, 200, user.toView());
                })
                .failureHandler((req, res, ex) -> writeJson(res, mapper, 401, "Invalid email or password."))
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_NO_CONTENT)))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler((req, res, e) -> writeJson(res, mapper, 403,
                        "The request was rejected (missing or invalid security token). Please reload the page.")))
            .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                            + "img-src 'self' data:; font-src 'self'; object-src 'none'; "
                            + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        // CSRF protection stays at Spring Security's secure default (enabled, token stored in the session).
        return http.build();
    }

    /** Writes either a plain message or an object as JSON. */
    private static void writeJson(HttpServletResponse res, ObjectMapper mapper, int status, Object payload)
            throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        Object body = payload instanceof String message
                ? Map.of("status", status, "message", message)
                : payload;
        mapper.writeValue(res.getWriter(), body);
    }
}
