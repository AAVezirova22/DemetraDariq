package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.Role;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + email));
        return new AppUserPrincipal(user);
    }

    @Transactional
    public User register(String name, String email, String rawPassword, Role role) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), name.trim(), role);
        return userRepository.save(user);
    }
}
