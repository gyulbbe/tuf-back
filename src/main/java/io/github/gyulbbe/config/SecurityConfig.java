package io.github.gyulbbe.config;

import io.github.gyulbbe.common.error.ApiErrorCode;
import io.github.gyulbbe.common.error.ApiErrorResponseWriter;
import io.github.gyulbbe.jwt.JWTFilter;
import io.github.gyulbbe.jwt.JWTUtil;
import io.github.gyulbbe.jwt.LoginFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final JWTUtil jwtUtil;

    @Value("${tuf-front.url}")
    private String frontUrl;

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(request -> {
            CorsConfiguration corsConfiguration = new CorsConfiguration();
            corsConfiguration.setAllowedOrigins(parseAllowedOrigins(frontUrl));
            corsConfiguration.setAllowCredentials(true);
            corsConfiguration.setAllowedHeaders(Collections.singletonList("*"));
            corsConfiguration.setAllowedMethods(Collections.singletonList("*"));
            corsConfiguration.setExposedHeaders(Collections.singletonList("Authorization"));
            corsConfiguration.setMaxAge(3600L);
            return corsConfiguration;
        }));

        http.csrf(auth -> auth.disable());

        http.formLogin(auth -> auth.disable());

        http.httpBasic(auth -> auth.disable());

        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                        ApiErrorResponseWriter.write(response, ApiErrorCode.AUTH_REQUIRED)
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        ApiErrorResponseWriter.write(response, ApiErrorCode.AUTH_FORBIDDEN)
                )
        );

//        http.authorizeHttpRequests((auth) -> auth
//                .requestMatchers("/login").permitAll()
//                .requestMatchers("/admin").hasAnyRole("MANAGER", "MASTER", "ADMIN")
//                .anyRequest().authenticated());

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/home/main").permitAll()
                .requestMatchers(HttpMethod.GET, "/home/schedules").permitAll()
                .requestMatchers(HttpMethod.GET, "/home/schedules/*/redirect").permitAll()
                .requestMatchers(HttpMethod.PUT, "/admin/menu-visibility").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers("/admin/home/schedules", "/admin/home/schedules/**").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers("/admin/maps", "/admin/maps/**").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers("/admin/proleagues", "/admin/proleagues/**").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/tournaments").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/tournaments/*/matches/*/score-submissions/*/approve").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/tournaments/*/matches/*/score-submissions/*/reject").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/tournaments/*/matches/*/score-submissions").authenticated()
                .requestMatchers(HttpMethod.GET, "/tournaments/*/matches/*/score-submissions").authenticated()
                .requestMatchers("/user/admin", "/user/admin/**").hasAnyRole("MANAGER", "MASTER", "ADMIN")
                .anyRequest().permitAll());

        http.addFilterBefore(new JWTFilter(jwtUtil), LoginFilter.class);

        http.addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtil), UsernamePasswordAuthenticationFilter.class);

        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    private List<String> parseAllowedOrigins(String configuredOrigins) {
        return Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }
}
