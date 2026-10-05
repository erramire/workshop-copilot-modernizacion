package org.sample.azure.student.presentation;

import java.util.List;

import jakarta.validation.Valid;

import org.sample.azure.student.application.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.TransactionException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/app")
public class StudentController {

    static final String LOAD_ERROR_MESSAGE = "Unable to load student data.";
    static final String SAVE_ERROR_MESSAGE = "Failed to save student. Please try again.";

    private static final Logger log = LoggerFactory.getLogger(StudentController.class);

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @InitBinder("studentForm")
    void trimFormValues(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping({"", "/", "/students"})
    public String listStudents(Model model) {
        try {
            model.addAttribute("students", studentService.listStudents());
        } catch (DataAccessException | TransactionException ex) {
            log.error("Unable to load students", ex);
            model.addAttribute("students", List.of());
            model.addAttribute("error", LOAD_ERROR_MESSAGE);
        }
        return "students/list";
    }

    @GetMapping("/add-student")
    public String showAddStudentForm(Model model) {
        model.addAttribute("studentForm", new StudentForm());
        return "students/form";
    }

    @PostMapping("/add-student")
    public String addStudent(@Valid @ModelAttribute("studentForm") StudentForm form,
                             BindingResult bindingResult,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "students/form";
        }
        try {
            studentService.register(form.getName(), form.getEmail(), form.getMajor());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Student " + form.getName() + " has been added successfully!");
        } catch (DataAccessException | TransactionException ex) {
            log.error("Failed to register student", ex);
            redirectAttributes.addFlashAttribute("errorMessage", SAVE_ERROR_MESSAGE);
        }
        return "redirect:/app/";
    }
}
