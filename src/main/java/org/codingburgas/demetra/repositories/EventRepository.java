package org.codingburgas.demetra.repositories;

import org.codingburgas.demetra.models.Event;
import org.codingburgas.demetra.models.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByOrganizerIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAscIdAsc(
            Long organizerId, java.time.LocalDateTime from, java.time.LocalDateTime until);
    List<Event> findByStatus(EventStatus status);
}
