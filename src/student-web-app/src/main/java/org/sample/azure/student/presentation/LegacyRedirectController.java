package org.sample.azure.student.presentation;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Compatibilidad con las URLs de los servlets legacy (ADR-007); 302 para permitir rollback
@Controller
public class LegacyRedirectController {

    @GetMapping("/")
    public String home() {
        return "redirect:/app/";
    }

    @GetMapping("/studentProfileList")
    public String studentProfileList() {
        return "redirect:/app/students";
    }

    @GetMapping("/addStudent")
    public String addStudent() {
        return "redirect:/app/add-student";
    }
}
