package org.codingburgas.demetra;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.*;
import org.codingburgas.demetra.services.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ReportsFlowTests {
    @Autowired RegistrationRepository registrations;
    @Autowired EventRepository events;
    @Autowired UserRepository users;
    @Autowired PaymentRepository payments;
    @Autowired OrderFilterService orders;
    @Autowired ReportService reports;
    @Autowired ReportPdfService pdf;
    @Autowired MockMvc mvc;
    User owner, other, student;
    @BeforeEach void setup() {
        owner = users.save(new User("report-owner@test.bg", "unused", "Организатор", Role.ORGANIZER));
        other = users.save(new User("report-other@test.bg", "unused", "Друг", Role.ORGANIZER));
        student = users.save(new User("report-student@test.bg", "unused", "Участник", Role.STUDENT));
    }
    Event event(User organizer, LocalDateTime date) {
        Event e = new Event(); e.setTitle("Благотворителен концерт"); e.setCategory("Концерт");
        e.setOrganizer(organizer); e.setStartsAt(date); e.setCapacity(10); e.setPrice(25.50);
        return events.save(e);
    }
    Registration booking(Event e, LocalDateTime date, RegistrationStatus status) {
        Registration r = new Registration(student, e, status); r.setCreatedAt(date); return registrations.save(r);
    }
    @Test void filtersIsoWeekDateDayHourAndBothSortDirections() {
        Event e = event(owner, LocalDateTime.now());
        var a = booking(e, LocalDateTime.of(2025,12,29,10,0), RegistrationStatus.CONFIRMED);
        var b = booking(event(owner, LocalDateTime.now()), LocalDateTime.of(2025,12,29,10,59,59), RegistrationStatus.CONFIRMED);
        booking(event(owner, LocalDateTime.now()), LocalDateTime.of(2025,12,29,11,0), RegistrationStatus.CONFIRMED);
        booking(event(owner, LocalDateTime.now()), LocalDateTime.of(2026,1,5,10,0), RegistrationStatus.CONFIRMED);
        booking(event(other, LocalDateTime.now()), LocalDateTime.of(2025,12,29,10,30), RegistrationStatus.CONFIRMED);
        assertEquals(List.of(a.getId(), b.getId()), orders.filterAndSort(owner.getId(), "2026-W01",
                LocalDate.of(2025,12,29), 1, 10, "asc").stream().map(Registration::getId).toList());
        assertEquals(List.of(b.getId(), a.getId()), orders.filterAndSort(owner.getId(), "2026-W01",
                LocalDate.of(2025,12,29), 1, 10, "desc").stream().map(Registration::getId).toList());
        assertTrue(orders.filterAndSort(owner.getId(), null, LocalDate.of(2025,12,29), 2, null, "asc").isEmpty());
    }
    @Test void rejectsInvalidOrderFilters() {
        assertThrows(ResponseStatusException.class, () -> orders.filterAndSort(owner.getId(), "2026-W99", null, null, null, "asc"));
        assertThrows(ResponseStatusException.class, () -> orders.filterAndSort(owner.getId(), null, null, 8, null, "asc"));
        assertThrows(ResponseStatusException.class, () -> orders.filterAndSort(owner.getId(), null, null, null, 24, "asc"));
        assertThrows(ResponseStatusException.class, () -> orders.filterAndSort(owner.getId(), null, null, null, null, "bogus"));
    }
    @Test void isoWeekIncludesSundayAndSortUsesIdForEqualTimes() {
        var time = LocalDateTime.of(2026,1,4,23,59,59);
        var a = booking(event(owner, null), time, RegistrationStatus.CONFIRMED);
        var b = booking(event(owner, null), time, RegistrationStatus.CONFIRMED);
        booking(event(owner, null), time.plusSeconds(1), RegistrationStatus.CONFIRMED);
        assertEquals(List.of(a, b), orders.filterAndSort(owner.getId(), "2026-W01", null, 7, 23, "asc"));
        assertEquals(List.of(b, a), orders.filterAndSort(owner.getId(), "2026-W01", null, 7, 23, "desc"));
    }
    @Test void reportsWholeMonthsBoundariesAndRecordedMoneyOnly() {
        YearMonth now = YearMonth.now();
        var first = event(owner, now.minusMonths(5).atDay(1).atStartOfDay());
        var last = event(owner, now.atEndOfMonth().atTime(23,59,59));
        event(owner, now.minusMonths(5).atDay(1).atStartOfDay().minusSeconds(1));
        event(owner, now.plusMonths(1).atDay(1).atStartOfDay());
        event(owner, null);
        event(other, now.atDay(1).atStartOfDay());
        var paid = booking(first, LocalDateTime.now(), RegistrationStatus.CONFIRMED);
        booking(last, LocalDateTime.now(), RegistrationStatus.CONFIRMED);
        orders.acknowledgePayment(owner.getId(), paid.getId());
        orders.acknowledgePayment(owner.getId(), paid.getId());
        first.setPrice(999); events.save(first);
        var result = reports.generate(owner.getId(), now.atDay(1), 6);
        assertEquals(2, result.events().size()); assertEquals(6, result.monthly().size());
        assertEquals(2, result.confirmed()); assertEquals(0, result.collected().compareTo(new BigDecimal("25.50")));
        assertEquals(0, result.monthly().get(1).events());
        assertEquals(1, payments.findByRegistrationEventOrganizerId(owner.getId()).size());
        assertEquals(1, reports.generate(owner.getId(), LocalDate.now(), 1).events().size());
        assertEquals(12, reports.generate(owner.getId(), LocalDate.now(), 12).monthly().size());
    }
    @Test void validatesReportWindowAndEmptyReport() {
        assertThrows(ResponseStatusException.class, () -> reports.generate(owner.getId(), LocalDate.now(), 3));
        assertThrows(ResponseStatusException.class, () -> reports.generate(owner.getId(), LocalDate.now().plusMonths(1), 1));
        assertThrows(ResponseStatusException.class, () -> reports.generate(owner.getId(), LocalDate.now().minusMonths(12), 1));
        assertThrows(ResponseStatusException.class, () -> reports.generate(owner.getId(), LocalDate.now().minusMonths(1), 12));
        var result = reports.generate(owner.getId(), LocalDate.now(), 1);
        assertTrue(result.events().isEmpty()); assertEquals(0, result.collected().signum());
    }
    @Test void paymentRequiresOwnerConfirmedStatusAndCsrf() throws Exception {
        var r = booking(event(owner, LocalDateTime.now()), LocalDateTime.now(), RegistrationStatus.WAITLISTED);
        assertThrows(ResponseStatusException.class, () -> orders.acknowledgePayment(owner.getId(), r.getId()));
        assertThrows(ResponseStatusException.class, () -> orders.acknowledgePayment(other.getId(), r.getId()));
        mvc.perform(post("/orders/" + r.getId() + "/paid").with(user(new AppUserPrincipal(owner))))
                .andExpect(status().isForbidden());
    }
    @Test void rendersHtmlAndExportsMatchingPdfWithDownloadHeaders() throws Exception {
        var r = booking(event(owner, LocalDate.now().atTime(18,30)), LocalDateTime.now(), RegistrationStatus.CONFIRMED);
        mvc.perform(post("/orders/" + r.getId() + "/paid").with(user(new AppUserPrincipal(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/orders").with(user(new AppUserPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Благотворителен концерт")));
        mvc.perform(get("/reports").with(user(new AppUserPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Изтегли PDF")));
        byte[] bytes = mvc.perform(get("/reports/pdf?download=true").with(user(new AppUserPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        try (var doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Благотворителен концерт")); assertTrue(text.contains("25.50 USD"));
        }
        mvc.perform(get("/reports/pdf").with(user(new AppUserPrincipal(owner))))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
        mvc.perform(get("/reports?months=3").with(user(new AppUserPrincipal(owner))))
                .andExpect(content().string(containsString("Изберете 1, 6 или 12")));
        mvc.perform(get("/reports/pdf?months=3").with(user(new AppUserPrincipal(owner))))
                .andExpect(status().isBadRequest());
    }
    @Test void organizerDataCannotBeReadByOtherOrganizerOrStudent() throws Exception {
        booking(event(owner, LocalDateTime.now()), LocalDateTime.now(), RegistrationStatus.CONFIRMED);
        mvc.perform(get("/reports").with(user(new AppUserPrincipal(other))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("Благотворителен концерт"))));
        for (String path : List.of("/orders", "/reports", "/reports/pdf")) {
            mvc.perform(get(path).with(user(new AppUserPrincipal(student)))).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        }
    }
    @Test void pdfIncludesAllEventsAcrossPagesAndHandlesLongUnicodeTitles() throws Exception {
        for (int i=0; i<24; i++) {
            Event e = event(owner, LocalDate.now().atTime(18,0));
            e.setTitle("Събитие " + i + " " + "дълго".repeat(25) + " 🎵"); events.save(e);
        }
        try (var doc = Loader.loadPDF(pdf.generate(reports.generate(owner.getId(), LocalDate.now(), 12)))) {
            assertTrue(doc.getNumberOfPages() > 2);
            String text = new PDFTextStripper().getText(doc);
            for (int i=0; i<24; i++) assertTrue(text.contains("Събитие " + i + " "));
        }
    }
}
