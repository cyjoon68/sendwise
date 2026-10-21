package com.sendwise.api.presentation;

import com.sendwise.api.application.SenderService;
import com.sendwise.api.domain.ApiError;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/sender-numbers")
public class SenderNumberPageController {
    private final SenderService senders;

    public SenderNumberPageController(SenderService senders) {
        this.senders = senders;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") long clientId, @RequestParam(required = false) String error, Model model) {
        model.addAttribute("clientId", clientId);
        model.addAttribute("numbers", senders.list(clientId));
        model.addAttribute("error", error);
        return "sender-numbers";
    }

    @PostMapping
    public String register(@RequestParam long clientId, @RequestParam String phone, RedirectAttributes redirect) {
        try {
            senders.register(clientId, phone);
        } catch (ApiError error) {
            redirect.addAttribute("error", error.getMessage());
        }
        redirect.addAttribute("clientId", clientId);
        return "redirect:/sender-numbers";
    }

    @PostMapping("/{id}/review")
    public String review(@PathVariable long id, @RequestParam long clientId, RedirectAttributes redirect) {
        return move(id, clientId, "REVIEWING", null, redirect);
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable long id, @RequestParam long clientId, RedirectAttributes redirect) {
        return move(id, clientId, "APPROVED", null, redirect);
    }

    @PostMapping("/{id}/reject")
    public String reject(
            @PathVariable long id,
            @RequestParam long clientId,
            @RequestParam(required = false) String rejectReason,
            RedirectAttributes redirect
    ) {
        return move(id, clientId, "REJECTED", rejectReason, redirect);
    }

    private String move(long id, long clientId, String status, String reason, RedirectAttributes redirect) {
        try {
            senders.transition(id, status, reason);
        } catch (ApiError error) {
            redirect.addAttribute("error", error.getMessage());
        }
        redirect.addAttribute("clientId", clientId);
        return "redirect:/sender-numbers";
    }
}
