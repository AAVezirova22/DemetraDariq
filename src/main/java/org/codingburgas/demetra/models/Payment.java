package org.codingburgas.demetra.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A manually acknowledged receipt, not a payment gateway transaction. */
@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, unique = true)
    private Registration registration;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();
}
