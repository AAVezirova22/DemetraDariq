package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.services.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.io.IOException;
import java.time.*;

@Controller
public class ReportController {
    private final ReportService reports;
    private final ReportPdfService pdf;
    public ReportController(ReportService reports, ReportPdfService pdf) { this.reports = reports; this.pdf = pdf; }
    @GetMapping("/reports")
    public String index(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
                        @RequestParam(required = false) LocalDate date, @RequestParam(defaultValue = "1") int months) {
        LocalDate selected = date == null ? LocalDate.now() : date;
        model.addAttribute("date", selected); model.addAttribute("months", months);
        model.addAttribute("minDate", YearMonth.now().minusMonths(11).atDay(1));
        model.addAttribute("maxDate", LocalDate.now());
        try {
            model.addAttribute("report", reports.generate(principal.getUser().getId(), selected, months));
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            model.addAttribute("error", ex.getReason());
        }
        return "reports/index";
    }
    @GetMapping(value = "/reports/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @RequestParam(required = false) LocalDate date,
                                        @RequestParam(defaultValue = "1") int months,
                                        @RequestParam(defaultValue = "false") boolean download) throws IOException {
        ReportService.Report report = reports.generate(principal.getUser().getId(), date == null ? LocalDate.now() : date, months);
        String name = "demetra-report-" + report.from() + "-" + report.until().minusDays(1) + ".pdf";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        (download ? "attachment" : "inline") + "; filename=\"" + name + "\"")
                .cacheControl(CacheControl.noStore()).body(pdf.generate(report));
    }
}
