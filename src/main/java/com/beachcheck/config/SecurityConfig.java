package com.beachcheck.config;

import com.beachcheck.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// ✅ CORS bean용 import
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .authorizeHttpRequests(auth -> auth
                        // ✅ CORS preflight 허용 (가장 위에)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ 디버그/문서/헬스는 전부 공개
                        .requestMatchers("/api/_debug/**").permitAll()
                        .requestMatchers("/actuator/**", "/swagger-ui.html", "/swagger-ui/**", "/api/docs/**").permitAll()

                        // ✅ 인증 엔드포인트 공개
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()

                        // ✅ 해변 조회는 공개, 찜 토글(임시)도 공개
                        .requestMatchers(HttpMethod.GET,  "/api/beaches/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/favorites/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/favorites/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/favorites/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/favorites/**").authenticated()

                        // ✅ 관리자만
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // 그 외는 인증 필요
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ✅ CORS 허용(프론트 로컬에서 호출 가능하도록)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 필요시 여기 리스트만 조정
        config.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
