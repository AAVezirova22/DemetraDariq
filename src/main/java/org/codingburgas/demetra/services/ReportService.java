package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Task 2: full calendar months, using the event date and only the owner's events. */
@Service
public class ReportService {
    public record EventRow(Long id, String title, String category, LocalDateTime startsAt,
                           String status, long confirmed, long waitlisted, long cancelled, BigDecimal collected) {}
    public record MonthRow(YearMonth month, long events, long confirmed, BigDecimal collected) {}
    public record Report(LocalDate from, LocalDate until, int months, List<EventRow> events,
                         List<MonthRow> monthly, long confirmed, BigDecimal collected) {}
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    public ReportService(EventRepository events, RegistrationRepository registrations, PaymentRepository payments) {
        this.events = events; this.registrations = registrations; this.payments = payments;
    }

    @Transactional(readOnly = true)
    public Report generate(Long organizerId, LocalDate selectedDate, int months) {
        if (!Set.of(1, 6, 12).contains(months))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Изберете 1, 6 или 12 месеца.");
        YearMonth current = YearMonth.now();
        YearMonth last = YearMonth.from(selectedDate);
        YearMonth first = last.minusMonths(months - 1L);
        if (last.isAfter(current) || first.isBefore(current.minusMonths(11)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Периодът трябва да е в последните 12 календарни месеца.");
        LocalDate from = first.atDay(1);
        LocalDate until = last.plusMonths(1).atDay(1);
        Map<Long, List<Registration>> byEvent = registrations.findByEventOrganizerId(organizerId).stream()
                .collect(Collectors.groupingBy(r -> r.getEvent().getId()));
        Map<Long, BigDecimal> received = payments.findByRegistrationEventOrganizerId(organizerId).stream()
                .collect(Collectors.groupingBy(p -> p.getRegistration().getEvent().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));
        List<EventRow> rows = events
                .findByOrganizerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
                        organizerId, from.atStartOfDay(), until.atStartOfDay()).stream()
                .map(e -> {
                    List<Registration> rs = byEvent.getOrDefault(e.getId(), List.of());
                    return new EventRow(e.getId(), e.getTitle(), e.getCategory(), e.getStartsAt(), e.getStatus().name(),
                            count(rs, RegistrationStatus.CONFIRMED), count(rs, RegistrationStatus.WAITLISTED),
                            count(rs, RegistrationStatus.CANCELLED), received.getOrDefault(e.getId(), BigDecimal.ZERO));
                }).toList();
        List<MonthRow> monthly = new ArrayList<>();
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            YearMonth month = m;
            List<EventRow> items = rows.stream().filter(r -> YearMonth.from(r.startsAt()).equals(month)).toList();
            monthly.add(new MonthRow(m, items.size(), items.stream().mapToLong(EventRow::confirmed).sum(),
                    items.stream().map(EventRow::collected).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        return new Report(from, until, months, rows, List.copyOf(monthly),
                rows.stream().mapToLong(EventRow::confirmed).sum(),
                rows.stream().map(EventRow::collected).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private long count(List<Registration> rows, RegistrationStatus status) {
        return rows.stream().filter(r -> r.getStatus() == status).count();
    }
}
