package com.zc.agent.registry.config;

import com.zc.agent.registry.security.JwtAuthenticationFilter;
import com.zc.agent.registry.security.JwtProperties;
import com.zc.agent.registry.security.RestAuthenticationEntryPoint;
import com.zc.agent.registry.security.RegistryServiceAuthenticationFilter;
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
@EnableConfigurationProperties({
        JwtProperties.class,
        ToolSecurityProperties.class,
        RegistryRuntimeSecurityProperties.class
})
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
                                                   RegistryServiceAuthenticationFilter serviceAuthenticationFilter,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(configurer -> configurer.authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(configurer -> configurer
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/registry/auth/login").permitAll()
                        .requestMatchers("/api/registry/auth/me").authenticated()
                        .requestMatchers("/api/registry/users/**", "/api/registry/roles/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/registry/models/code/*/runtime", "/api/registry/tools/*/runtime").hasAuthority("SERVICE_RUNTIME")
                        .requestMatchers("/api/registry/runtime-plugins/runtime/**").hasAuthority("SERVICE_RUNTIME")
                        .requestMatchers("/api/registry/knowledge-bases/runtime/**").hasAuthority("SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/models/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_model", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/agents/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_agent", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/runtime-plugins/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/tools/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/tool-capability-packs/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool")
                        .requestMatchers(HttpMethod.GET, "/api/registry/workflows/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_workflow", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/prompts/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_prompt", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/guardrail-policies/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_guardrail", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/knowledge-bases/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_knowledge", "SERVICE_RUNTIME")
                        .requestMatchers(HttpMethod.GET, "/api/registry/dict-types/**", "/api/registry/dict-items/**", "/api/registry/dicts/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_dict", "SERVICE_RUNTIME")
                        .requestMatchers("/api/registry/models/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_model")
                        .requestMatchers("/api/registry/agents/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_agent")
                        .requestMatchers("/api/registry/runtime-plugins/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool")
                        .requestMatchers("/api/registry/tools/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool")
                        .requestMatchers("/api/registry/tool-capability-packs/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_tool")
                        .requestMatchers("/api/registry/workflows/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_workflow")
                        .requestMatchers("/api/registry/prompts/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_prompt")
                        .requestMatchers("/api/registry/guardrail-policies/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_guardrail")
                        .requestMatchers("/api/registry/knowledge-bases/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_knowledge")
                        .requestMatchers("/api/registry/dict-types/**", "/api/registry/dict-items/**", "/api/registry/dicts/**").hasAnyAuthority("ROLE_ADMIN", "SCOPE_dict")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(serviceAuthenticationFilter, JwtAuthenticationFilter.class);

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
