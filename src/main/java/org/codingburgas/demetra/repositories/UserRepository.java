package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByRole(Role role);
}
