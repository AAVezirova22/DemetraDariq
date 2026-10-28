package org.codingburgas.demetra;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.EventRepository;
import org.codingburgas.demetra.repositories.UserRepository;
import org.codingburgas.demetra.services.AppUserPrincipal;
import org.codingburgas.demetra.services.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class EventSearchTests {
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired EventService service;
    @Autowired MockMvc mvc;
    User organizer;

    @BeforeEach void setup() {
        organizer = users.save(new User("event-search@test.bg", "unused", "Организатор", Role.ORGANIZER));
    }

    Event event(String title, LocalDateTime start, LocalDateTime created) {
        Event e = new Event();
        e.setTitle(title); e.setOrganizer(organizer); e.setCapacity(10);
        e.setStartsAt(start); e.setCreatedAt(created);
        return events.save(e);
    }

    @Test void filtersStartTimeAndSortsCreationTimeInBothDirections() {
        var created = LocalDateTime.of(2026, 1, 1, 12, 0);
        var a = event("Ранен час, по-ново събитие", LocalDateTime.of(2025,12,29,9,0), created.plusHours(1));
        var b = event("Късен час, по-старо събитие", LocalDateTime.of(2025,12,29,9,59,59,999999999), created);
        event("Следващ час", LocalDateTime.of(2025,12,29,10,0), created);
        event("Следваща седмица", LocalDateTime.of(2026,1,5,9,0), created);
        event("Без дата", null, created);
        assertEquals(List.of(b, a), service.filterAndSort("2026-W01", LocalDate.of(2025,12,29), 1, 9, "asc"));
        assertEquals(List.of(a, b), service.filterAndSort("2026-W01", LocalDate.of(2025,12,29), 1, 9, "desc"));
        assertTrue(service.filterAndSort("2026-W01", LocalDate.of(2025,12,29), 2, 9, "asc").isEmpty());
    }

    @Test void includesSundayAndBreaksCreationTimeTiesById() {
        var start = LocalDateTime.of(2026,1,4,23,59,59);
        var created = LocalDateTime.of(2025,10,1,10,0);
        var a = event("Първо", start, created);
        var b = event("Второ", start, created);
        event("След понеделник", start.plusSeconds(1), created);
        assertEquals(List.of(a, b), service.filterAndSort("2026-W01", null, 7, 23, "asc"));
        assertEquals(List.of(b, a), service.filterAndSort("2026-W01", null, 7, 23, "desc"));
    }

    @Test void individualFiltersWorkIndependently() {
        var start = LocalDateTime.of(2026,10,16,9,15);
        var match = event("Есенен концерт", start, start.minusMonths(1));
        var other = event("Друг ден", start.plusDays(1).plusHours(1), start);
        event("Без дата", null, start);
        assertEquals(List.of(match), service.filterAndSort(null, start.toLocalDate(), null, null, "asc"));
        assertEquals(List.of(match), service.filterAndSort(null, null, 5, null, "asc"));
        assertEquals(List.of(match), service.filterAndSort(null, null, null, 9, "asc"));
        assertEquals(List.of(match, other), service.filterAndSort("2026-W42", null, null, null, "asc"));
    }

    @Test void searchShowsPublishedEventsFromAllOrganizersAndClearRemovesFilters() throws Exception {
        var start = LocalDateTime.of(2026,10,16,9,15);
        var match = event("Есенен концерт", start, start.minusMonths(1));
        var tba = event("Без начална дата", null, start);
        var otherOrganizer = users.save(new User("event-search-other@test.bg", "unused", "Друг", Role.ORGANIZER));
        tba.setOrganizer(otherOrganizer); events.save(tba);
        for (var state : List.of(EventStatus.DRAFT, EventStatus.CANCELLED, EventStatus.CLOSED)) {
            var hidden = event("Непубликувано " + state, start, start);
            hidden.setStatus(state); events.save(hidden);
        }
        mvc.perform(get("/events?week=2026-W42&date=2026-10-16&day=5&hour=9&sort=asc"))
                .andExpect(status().isOk()).andExpect(view().name("events/list"))
                .andExpect(model().attribute("events", List.of(match)))
                .andExpect(model().attributeDoesNotExist("orders"))
                .andExpect(content().string(containsString("Намерени събития: 1")))
                .andExpect(content().string(containsString("<h3>Есенен концерт</h3>")))
                .andExpect(content().string(not(containsString("Без начална дата"))))
                .andExpect(content().string(containsString("name=\"week\" value=\"2026-W42\"")))
                .andExpect(content().string(containsString("name=\"date\" value=\"2026-10-16\"")))
                .andExpect(content().string(containsString("value=\"5\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"9\" selected=\"selected\"")))
                .andExpect(content().string(containsString("value=\"asc\" selected=\"selected\"")))
                .andExpect(content().string(containsString("href=\"/events\" class=\"btn btn-secondary\">Изчисти</a>")));
        mvc.perform(get("/events").with(user(new AppUserPrincipal(organizer))))
                .andExpect(status().isOk()).andExpect(model().attribute("events", List.of(tba, match)));
        var student = users.save(new User("event-search-student@test.bg", "unused", "Участник", Role.STUDENT));
        mvc.perform(get("/events").with(user(new AppUserPrincipal(student))))
                .andExpect(status().isOk()).andExpect(model().attribute("events", List.of(tba, match)))
                .andExpect(content().string(containsString("name=\"week\"")));
        mvc.perform(get("/events?week=&date=&day=&hour=&sort="))
                .andExpect(status().isOk()).andExpect(model().attribute("events", List.of(tba, match)));
    }

    @Test void rendersEmptySearchAndRejectsInvalidFilters() throws Exception {
        mvc.perform(get("/events?date=2026-10-16&day=1"))
                .andExpect(status().isOk()).andExpect(model().attribute("events", List.of()))
                .andExpect(content().string(containsString("Няма събития за избраните филтри.")));
        for (String query : List.of("week=2026-W99", "week=2026-W00", "week=2026-W54", "week=invalid",
                "date=invalid", "day=0", "day=8", "hour=-1", "hour=24", "sort=invalid")) {
            mvc.perform(get("/events?" + query)).andExpect(status().isBadRequest());
        }
    }
}
