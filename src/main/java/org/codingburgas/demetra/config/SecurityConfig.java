package org.codingburgas.demetra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/register", "/login", "/css/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/photos", "/photos/**").hasRole("ADMIN")
                        .requestMatchers("/events/new").hasRole("ORGANIZER")
                        .requestMatchers("/orders", "/orders/**", "/reports", "/reports/**").hasRole("ORGANIZER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/events", "/events/*").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/events", false)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/events?loggedOut")
                        .permitAll()
                );

        return http.build();
    }
}
