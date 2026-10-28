package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/** Task 1: orders are the existing event registrations, filtered by booking time. */
@Service
public class OrderFilterService {
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    public OrderFilterService(RegistrationRepository registrations, PaymentRepository payments) {
        this.registrations = registrations;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<Registration> filterAndSort(Long organizerId, String week, LocalDate date,
                                           Integer day, Integer hour, String sort) {
        if (day != null && (day < 1 || day > 7) || hour != null && (hour < 0 || hour > 23)
                || !Set.of("asc", "desc").contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Невалиден ден, час или сортировка.");
        }
        LocalDate weekStart = null;
        if (week != null && !week.isBlank()) {
            try {
                weekStart = LocalDate.parse(week + "-1", DateTimeFormatter.ISO_WEEK_DATE);
            } catch (DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Седмицата трябва да е YYYY-Www.");
            }
        }
        final LocalDate monday = weekStart;
        Comparator<Registration> order = Comparator.comparing(Registration::getCreatedAt)
                .thenComparing(Registration::getId);
        if ("desc".equals(sort)) order = order.reversed();
        return registrations.findByEventOrganizerId(organizerId).stream()
                .filter(r -> monday == null || (!r.getCreatedAt().toLocalDate().isBefore(monday)
                        && r.getCreatedAt().toLocalDate().isBefore(monday.plusWeeks(1))))
                .filter(r -> date == null || r.getCreatedAt().toLocalDate().equals(date))
                .filter(r -> day == null || r.getCreatedAt().getDayOfWeek().getValue() == day)
                .filter(r -> hour == null || r.getCreatedAt().getHour() == hour)
                .sorted(order).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> paidAmounts(Long organizerId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        payments.findByRegistrationEventOrganizerId(organizerId)
                .forEach(p -> result.put(p.getRegistration().getId(), p.getAmount()));
        return result;
    }

    @Transactional
    public void acknowledgePayment(Long organizerId, Long registrationId) {
        Registration r = registrations.findById(registrationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!r.getEvent().getOrganizer().getId().equals(organizerId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (payments.existsByRegistrationId(registrationId)) return;
        if (r.getStatus() != RegistrationStatus.CONFIRMED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Само потвърдени регистрации могат да се платят.");
        Payment payment = new Payment();
        payment.setRegistration(r);
        payment.setAmount(BigDecimal.valueOf(r.getEvent().getPrice()).setScale(2, RoundingMode.HALF_UP));
        payments.save(payment);
    }
}
