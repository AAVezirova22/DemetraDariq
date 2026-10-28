package org.codingburgas.demetra.services;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Service;
import java.io.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Task 3: the same report as HTML, including every month and every event. */
@Service
public class ReportPdfService {
    public byte[] generate(ReportService.Report report) throws IOException {
        try (PDDocument document = new PDDocument();
             InputStream source = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            if (source == null) throw new IOException("Missing embedded report font");
            PDType0Font font = PDType0Font.load(document, source);
            List<String> lines = new ArrayList<>();
            lines.add("Demetra - Отчет за събития");
            lines.add("Период: " + report.from() + " до " + report.until().minusDays(1));
            lines.add("Събития: " + report.events().size() + " | Потвърдени регистрации: " + report.confirmed());
            lines.add("Общо получени плащания: " + money(report.collected()) + " USD");
            lines.add("Сумите са ръчно отбелязани получени плащания за събитията в периода.");
            lines.add("Не се включват неплатени регистрации. Няма модул за възстановявания.");
            lines.add("Събитията без дата не участват. Текущият месец може да е неприключил.");
            lines.add("");
            lines.add("МЕСЕЧНА СПРАВКА");
            for (var row : report.monthly()) {
                lines.add(row.month() + " | Събития: " + row.events() + " | Потвърдени: " + row.confirmed()
                        + " | Получени: " + money(row.collected()) + " USD");
            }
            lines.add("");
            lines.add("ВСИЧКИ СЪБИТИЯ В ПЕРИОДА");
            if (report.events().isEmpty()) lines.add("Няма събития за избрания период.");
            for (var row : report.events()) {
                lines.add("#" + row.id() + " " + row.title());
                lines.add("Дата: " + row.startsAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        + " | Вид: " + (row.category() == null ? "Без категория" : row.category()));
                lines.add("Статус: " + row.status() + " | Потвърдени: " + row.confirmed()
                        + " | Чакащи: " + row.waitlisted() + " | Отказани: " + row.cancelled());
                lines.add("Получени: " + money(row.collected()) + " USD");
                lines.add("");
            }
            List<String> wrapped = new ArrayList<>();
            for (String line : lines) wrap(line, font, 10, 499, wrapped);
            int pageNo = 0;
            for (int offset = 0; offset < wrapped.size(); offset += 43) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText(); stream.setFont(font, 10); stream.setLeading(16);
                    stream.newLineAtOffset(48, 790);
                    for (int i = offset; i < Math.min(offset + 43, wrapped.size()); i++) {
                        stream.showText(wrapped.get(i)); stream.newLine();
                    }
                    stream.endText();
                    stream.beginText(); stream.setFont(font, 9); stream.newLineAtOffset(48, 40);
                    stream.showText("Demetra | Страница " + (++pageNo)); stream.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
    private String money(java.math.BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private void wrap(String raw, PDType0Font font, float size, float width, List<String> target) throws IOException {
        // Replace control / unsupported glyphs so user supplied titles cannot break the export.
        StringBuilder line = new StringBuilder();
        for (int cp : raw.codePoints().toArray()) {
            String ch = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
            try { font.encode(ch); } catch (IllegalArgumentException ex) { ch = "?"; }
            if (font.getStringWidth(line + ch) * size / 1000 > width && !line.isEmpty()) {
                target.add(line.toString()); line.setLength(0);
            }
            line.append(ch);
        }
        target.add(line.toString());
    }
}
