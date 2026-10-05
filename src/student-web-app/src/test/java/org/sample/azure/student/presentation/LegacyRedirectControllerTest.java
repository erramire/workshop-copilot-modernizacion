package org.sample.azure.student.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sample.azure.student.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LegacyRedirectController.class)
@Import(SecurityConfig.class)
class LegacyRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @CsvSource({
            "/, /app/",
            "/studentProfileList, /app/students",
            "/addStudent, /app/add-student"
    })
    @DisplayName("AC-15: las URLs legacy de tipo GET redirigen con 302")
    void legacyUrlsRedirectToCanonicalUrls(String legacyUrl, String canonicalUrl) throws Exception {
        mockMvc.perform(get(legacyUrl))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(canonicalUrl));
    }

    @Test
    @DisplayName("AC-16: POST /addStudent queda retirada (403 sin token CSRF)")
    void legacyAddStudentPostIsRetired() throws Exception {
        mockMvc.perform(post("/addStudent")
                        .param("name", "Alice Johnson")
                        .param("email", "alice@example.com")
                        .param("major", "Biology"))
                .andExpect(status().isForbidden());
    }
}
