package br.com.lesnik.fcontrol.controller;

import br.com.lesnik.fcontrol.dto.LancamentoDTO;
import br.com.lesnik.fcontrol.model.Categoria;
import br.com.lesnik.fcontrol.service.ExportService;
import br.com.lesnik.fcontrol.service.LancamentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LancamentoController.class)
class LancamentoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LancamentoService service;

    @MockBean
    private ExportService exportService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Deve criar um novo lançamento com sucesso")
    void deveCriarLancamento() throws Exception {
        LancamentoDTO inputDto = LancamentoDTO.builder()
                .descricao("Internet")
                .categoria(Categoria.ASSINATURA)
                .subcategoria("Internet")
                .valor(new BigDecimal("100.00"))
                .mes(1)
                .ano(2026)
                .build();

        LancamentoDTO outputDto = LancamentoDTO.builder()
                .id(1L)
                .descricao("Internet")
                .categoria(Categoria.ASSINATURA)
                .subcategoria("Internet")
                .valor(new BigDecimal("100.00"))
                .mes(1)
                .ano(2026)
                .build();

        when(service.criar(any(LancamentoDTO.class))).thenReturn(outputDto);

        mockMvc.perform(post("/api/lancamentos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inputDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.subcategoria").value("Internet"))
                .andExpect(jsonPath("$.valor").value(100.00));
    }
}
