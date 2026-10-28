package org.codingburgas.demetra.services;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.codingburgas.demetra.models.Registration;
import org.codingburgas.demetra.models.RegistrationStatus;
import org.codingburgas.demetra.models.User;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;

    public EventService(EventRepository eventRepository, RegistrationRepository registrationRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
    }

    /** Filters published events by start time and sorts them by creation time. */
    @Transactional(readOnly = true)
    public List<Event> filterAndSort(String week, LocalDate date, Integer day, Integer hour, String sort) {
        if ((day != null && (day < 1 || day > 7)) || (hour != null && (hour < 0 || hour > 23))
                || !("asc".equals(sort) || "desc".equals(sort))) {
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
        boolean hasTimeFilter = monday != null || date != null || day != null || hour != null;
        Comparator<Event> order = Comparator.comparing(Event::getCreatedAt).thenComparing(Event::getId);
        if ("desc".equals(sort)) order = order.reversed();
        return eventRepository.findByStatus(EventStatus.PUBLISHED).stream()
                .filter(e -> !hasTimeFilter || e.getStartsAt() != null)
                .filter(e -> monday == null || (!e.getStartsAt().toLocalDate().isBefore(monday)
                        && e.getStartsAt().toLocalDate().isBefore(monday.plusWeeks(1))))
                .filter(e -> date == null || e.getStartsAt().toLocalDate().equals(date))
                .filter(e -> day == null || e.getStartsAt().getDayOfWeek().getValue() == day)
                .filter(e -> hour == null || e.getStartsAt().getHour() == hour)
                .sorted(order).toList();
    }

    public Optional<Event> findById(Long id) {
        return eventRepository.findById(id);
    }

    @Transactional
    public Event createEvent(Event event, User organizer) {
        event.setOrganizer(organizer);
        event.setStatus(EventStatus.PUBLISHED);
        return eventRepository.save(event);
    }

    public long confirmedCount(Event event) {
        return registrationRepository.countByEventAndStatus(event, RegistrationStatus.CONFIRMED);
    }

    public Optional<Registration> findRegistration(User user, Event event) {
        return registrationRepository.findByUserAndEvent(user, event);
    }

    public List<Registration> myRegistrations(User user) {
        return registrationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public Registration register(User user, Event event) {
        Optional<Registration> existing = registrationRepository.findByUserAndEvent(user, event);
        if (existing.isPresent() && existing.get().getStatus() != RegistrationStatus.CANCELLED) {
            throw new IllegalStateException("You are already registered for this event.");
        }

        long confirmed = confirmedCount(event);
        RegistrationStatus status = confirmed < event.getCapacity()
                ? RegistrationStatus.CONFIRMED
                : RegistrationStatus.WAITLISTED;

        if (existing.isPresent()) {
            Registration registration = existing.get();
            registration.setStatus(status);
            return registrationRepository.save(registration);
        }

        return registrationRepository.save(new Registration(user, event, status));
    }

    @Transactional
    public void cancelRegistration(User user, Event event) {
        Registration registration = registrationRepository.findByUserAndEvent(user, event)
                .filter(r -> r.getStatus() != RegistrationStatus.CANCELLED)
                .orElseThrow(() -> new IllegalStateException("You are not registered for this event."));

        boolean wasConfirmed = registration.getStatus() == RegistrationStatus.CONFIRMED;
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrationRepository.save(registration);

        if (wasConfirmed) {
            registrationRepository.findByEventAndStatusOrderByCreatedAtAsc(event, RegistrationStatus.WAITLISTED)
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setStatus(RegistrationStatus.CONFIRMED);
                        registrationRepository.save(next);
                    });
        }
    }
}
