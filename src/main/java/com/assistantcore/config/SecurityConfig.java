package com.assistantcore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  InMemoryUserDetailsManager inMemoryUserDetailsManager(
    @Value("${app.security.basic.username:assistant_admin}") String username,
    @Value("${app.security.basic.password:change_me_assistant_password}") String password,
    PasswordEncoder passwordEncoder
  ) {
    UserDetails user = User
      .withUsername(username)
      .password(passwordEncoder.encode(password))
      .roles("ADMIN")
      .build();

    return new InMemoryUserDetailsManager(user);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
      .csrf(csrf -> csrf.disable())
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(
          "/actuator/health",
          "/api/v1/health",
          "/api/v1/webhooks/**",
          "/api/v1/onboarding/mock",
          "/api/v1/oauth/google/calendar/callback"
        ).permitAll()
        .anyRequest().authenticated()
      )
      .httpBasic(Customizer.withDefaults());

    return http.build();
  }
}
