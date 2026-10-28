package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByRegistrationId(Long registrationId);
    @EntityGraph(attributePaths = {"registration", "registration.event"})
    List<Payment> findByRegistrationEventOrganizerId(Long organizerId);
}
