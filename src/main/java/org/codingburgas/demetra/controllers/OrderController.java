package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.services.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@Controller
public class OrderController {
    private final OrderFilterService orders;
    public OrderController(OrderFilterService orders) { this.orders = orders; }
    @GetMapping("/orders")
    public String list(@AuthenticationPrincipal AppUserPrincipal principal, Model model,
                       @RequestParam(required = false) String week,
                       @RequestParam(required = false) LocalDate date,
                       @RequestParam(required = false) Integer day,
                       @RequestParam(required = false) Integer hour,
                       @RequestParam(defaultValue = "desc") String sort) {
        Long id = principal.getUser().getId();
        model.addAttribute("orders", orders.filterAndSort(id, week, date, day, hour, sort));
        model.addAttribute("paid", orders.paidAmounts(id));
        model.addAttribute("week", week); model.addAttribute("date", date);
        model.addAttribute("day", day); model.addAttribute("hour", hour); model.addAttribute("sort", sort);
        return "orders/list";
    }
    @PostMapping("/orders/{id}/paid")
    public String paid(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        orders.acknowledgePayment(principal.getUser().getId(), id);
        return "redirect:/orders";
    }
}
