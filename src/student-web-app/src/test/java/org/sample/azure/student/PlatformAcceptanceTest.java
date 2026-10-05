package org.sample.azure.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway crea la tabla student_profiles al arrancar")
    void flywayCreatesStudentProfilesTable() {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM student_profiles", Integer.class);

        assertThat(rows).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/health", "/health/liveness", "/health/readiness"})
    @DisplayName("AC-18: los endpoints de health responden UP sin detalles")
    void healthEndpointsAreUp(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    @DisplayName("AC-17: una URL desconocida devuelve 404 con una página genérica")
    void unknownUrlReturnsGenericNotFoundPage() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("The page you requested was not found.")))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
