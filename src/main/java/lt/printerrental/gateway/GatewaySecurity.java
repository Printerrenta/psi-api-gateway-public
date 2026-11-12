package lt.printerrental.gateway;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;

@Configuration
public class GatewaySecurity {
  @Bean
  SecurityFilterChain gatewayFilterChain(HttpSecurity http,
      @Value("${GATEWAY_ALLOWED_ORIGINS:http://localhost:5173,http://127.0.0.1:5173}") String origins) throws Exception {
    var cors = new CorsConfiguration();
    var allowed = Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isBlank()).toList();
    if (allowed.isEmpty() || allowed.stream().anyMatch(s -> s.contains("*")))
      throw new IllegalArgumentException("Configure explicit gateway origins");
    cors.setAllowedOrigins(allowed);
    cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    cors.setExposedHeaders(List.of("Location", "Content-Disposition", "WWW-Authenticate"));
    cors.setAllowCredentials(false); // The browser sends an explicit user JWT, no gateway cookies.
    cors.setMaxAge(600L);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return http.cors(c -> c.configurationSource(source)).csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/api/**", "/hello", "/health", "/")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
