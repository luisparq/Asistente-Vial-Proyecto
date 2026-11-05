package com.avigem.diagnostics.infrastructure.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que Spring Boot sirve la interfaz estática en la raíz.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Interfaz estática")
class StaticInterfaceTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET / entrega la interfaz de prueba")
    void rootServesUi() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("AVIGEM Diagnostics")))
            .andExpect(content().string(containsString("Solo TEST_DATA")))
            .andExpect(content().string(containsString("Diagn")))
            .andExpect(content().string(containsString("Iniciar prueba")));
    }
}
