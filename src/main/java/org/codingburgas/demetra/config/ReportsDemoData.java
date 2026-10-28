package org.codingburgas.demetra.config;

import org.codingburgas.demetra.models.*;
import org.codingburgas.demetra.repositories.*;
import org.codingburgas.demetra.services.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.*;

/** Synthetic fixtures, enabled only with the explicit demo profile. */
@Component @Profile("demo")
public class ReportsDemoData implements CommandLineRunner {
    private final UserService users;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    public ReportsDemoData(UserService users, EventRepository events, RegistrationRepository registrations, PaymentRepository payments) {
        this.users = users; this.events = events; this.registrations = registrations; this.payments = payments;
    }
    @Override public void run(String... args) {
        User owner = users.register("Демо организатор", "reports@example.com", "ReportsDemo123", Role.ORGANIZER);
        User other = users.register("Друг организатор", "other@example.com", "ReportsDemo123", Role.ORGANIZER);
        User anna = users.register("Анна Иванова", "anna@example.com", "ReportsDemo123", Role.STUDENT);
        User boris = users.register("Борис Петров", "boris@example.com", "ReportsDemo123", Role.STUDENT);
        YearMonth current = YearMonth.now();
        String[] names = {"Есенен концерт", "Музикална работилница", "Благотворителен рецитал", "Летен фестивал", "Пролетен концерт", "Зимен рецитал"};
        int[] ago = {0, 0, 1, 3, 5, 10};
        for (int i = 0; i < names.length; i++) {
            Event event = new Event(); event.setTitle(names[i]); event.setOrganizer(owner);
            event.setCategory(i % 2 == 0 ? "Концерт" : "Работилница");
            event.setStartsAt(current.minusMonths(ago[i]).atDay(10 + i).atTime(18, 30));
            event.setCapacity(20); event.setPrice(i == 1 ? 15 : 25); event.setLocation("Бургас, зала Деметра");
            events.save(event);
            Registration a = new Registration(anna, event, RegistrationStatus.CONFIRMED);
            a.setCreatedAt(LocalDate.now().atTime(9 + i, 15)); registrations.save(a);
            Registration b = new Registration(boris, event, i == 1 ? RegistrationStatus.WAITLISTED : RegistrationStatus.CONFIRMED);
            b.setCreatedAt(LocalDate.now().minusDays(2).atTime(14, 30)); registrations.save(b);
            Payment p = new Payment(); p.setRegistration(a); p.setAmount(BigDecimal.valueOf(event.getPrice())); payments.save(p);
            if (i == 0) { Payment p2 = new Payment(); p2.setRegistration(b); p2.setAmount(BigDecimal.valueOf(event.getPrice())); payments.save(p2); }
        }
        Event hidden = new Event(); hidden.setTitle("Чуждо събитие"); hidden.setOrganizer(other);
        hidden.setCapacity(10); hidden.setPrice(999); hidden.setStartsAt(LocalDate.now().atTime(20, 0)); events.save(hidden);
    }
}
