package com.zc.agent.core.config;

import com.zc.agent.core.security.JwtAuthenticationFilter;
import com.zc.agent.core.security.JwtProperties;
import com.zc.agent.core.security.RestAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /**
     * 配置安全过滤链。
     *
     * @param http                    HttpSecurity
     * @param jwtAuthenticationFilter JWT 过滤器
     * @return 过滤链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(configurer -> configurer.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(configurer -> configurer
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/core/webhooks/waits/*").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/core/evals/release-gate/agents/*").permitAll()
                        .requestMatchers("/api/core/evals/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_eval")
                        .requestMatchers("/api/core/sandboxes/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_sandbox")
                        .requestMatchers("/api/core/traces/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tracing")
                        .requestMatchers("/api/core/guardrails/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_guardrail")
                        .requestMatchers("/api/core/tools/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool")
                        .requestMatchers("/api/core/knowledge/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_knowledge")
                        .requestMatchers("/api/core/memories/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_chat")
                        .requestMatchers("/api/core/approvals/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_chat", "SCOPE_approval")
                        .requestMatchers("/api/core/runtime/plugins/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool")
                        .requestMatchers("/api/core/runtime/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_chat")
                        .requestMatchers("/api/core/conversations/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_chat")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 配置跨域。
     *
     * @param jwtProperties JWT 配置
     * @return 跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(JwtProperties jwtProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(jwtProperties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
