package org.codingburgas.demetra;

import org.codingburgas.demetra.services.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/** Reproducible evidence: real controller/Thymeleaf output, using synthetic demo data. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reports_evidence;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles({"demo", "test"})
class ReportsEvidenceTests {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Test void captureRenderedApplicationResponses() throws Exception {
        Path dir = Path.of("target/report-evidence"); Files.createDirectories(dir);
        var principal = (AppUserPrincipal) users.loadUserByUsername("reports@example.com");
        save(dir, "orders-all", "/orders", principal);
        String week = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYY-'W'ww", java.util.Locale.UK));
        String filter = "/orders?week=" + week + "&date=" + LocalDate.now() + "&day="
                + LocalDate.now().getDayOfWeek().getValue() + "&hour=9&sort=asc";
        String filtered = save(dir, "orders-filtered", filter, principal);
        assertTrue(filtered.contains("Намерени поръчки: 1"));
        LocalDate eventDate = LocalDate.now().withDayOfMonth(10);
        String eventWeek = eventDate.format(DateTimeFormatter.ISO_WEEK_DATE).substring(0, 8);
        String eventFilter = "/events?week=" + eventWeek + "&date=" + eventDate + "&day="
                + eventDate.getDayOfWeek().getValue() + "&hour=18&sort=asc";
        save(dir, "events-all", "/events", principal);
        String eventFiltered = save(dir, "events-filtered", eventFilter, principal);
        assertTrue(eventFiltered.contains("Намерени събития: 1"));
        assertTrue(eventFiltered.contains("<h3>Есенен концерт</h3>"));
        assertFalse(eventFiltered.contains("<h3>Музикална работилница</h3>"));
        for (int months : new int[]{1,6,12}) {
            String html = save(dir, "reports-" + months, "/reports?date=" + LocalDate.now() + "&months=" + months, principal);
            assertTrue(html.contains("Есенен концерт")); assertFalse(html.contains("Чуждо събитие"));
        }
        save(dir, "reports-invalid", "/reports?date=" + LocalDate.now().minusMonths(1) + "&months=12", principal);
        var response = mvc.perform(get("/reports/pdf?months=12&download=true").with(user(principal)))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andReturn().getResponse();
        Files.write(dir.resolve("report-12.pdf"), response.getContentAsByteArray());
        Files.writeString(dir.resolve("pdf-headers.txt"), "Status: " + response.getStatus() + "\nContent-Type: "
                + response.getContentType() + "\nContent-Disposition: " + response.getHeader("Content-Disposition"));
        Files.writeString(dir.resolve("capture-info.txt"), "Captured: " + LocalDateTime.now() + "\nFilter: " + filter
                + "\nMode: Spring Boot MockMvc + Thymeleaf + isolated H2; synthetic demo data.\n");
        // Payment form is exercised via a real POST; the receipt becomes visible in the report.
        mvc.perform(post("/orders/4/paid").with(user(principal)).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post("/orders/6/paid").with(user(principal)).with(csrf())).andExpect(status().is3xxRedirection());
        save(dir, "orders-payment", "/orders", principal);
        save(dir, "reports-after-payment", "/reports?months=6", principal);
    }
    private String save(Path dir, String name, String path, AppUserPrincipal principal) throws Exception {
        MvcResult result = mvc.perform(get(path).with(user(principal))).andExpect(status().isOk()).andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Embed the actual project stylesheet so the captured response is viewable without a server.
        html = html.replace("<link rel=\"stylesheet\" href=\"/css/style.css\">",
                "<style>" + Files.readString(Path.of("src/main/resources/static/css/style.css")) + "</style>");
        Files.writeString(dir.resolve(name + ".html"), html);
        return html;
    }
}
