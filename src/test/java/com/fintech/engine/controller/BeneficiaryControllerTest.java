package com.fintech.engine.controller;
import com.fintech.engine.service.BeneficiaryService;
import com.fintech.engine.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(controllers=BeneficiaryController.class, excludeAutoConfiguration={SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import(GlobalExceptionHandler.class)
class BeneficiaryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BeneficiaryService service;
    @Test void usesAuthenticatedIdentity() throws Exception {
        when(service.list("owner@test.com")).thenReturn(List.of());
        mvc.perform(get("/api/v1/beneficiaries").principal(new TestingAuthenticationToken("owner@test.com", "pass")))
            .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).list("owner@test.com");
    }
    @Test void rejectsInvalidRequestBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/beneficiaries").principal(new TestingAuthenticationToken("owner@test.com", "pass"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BANK\",\"nickname\":\" \",\"currency\":\"invalid\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void returnsConflictForDuplicate() throws Exception {
        when(service.create(eq("owner@test.com"), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Beneficiary already saved"));
        mvc.perform(post("/api/v1/beneficiaries").principal(new TestingAuthenticationToken("owner@test.com", "pass"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"BANK\",\"nickname\":\"Alex\",\"currency\":\"USD\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Beneficiary already saved"));
    }
    @Test void deletesUsingAuthenticatedOwner() throws Exception {
        UUID id=UUID.randomUUID();
        mvc.perform(delete("/api/v1/beneficiaries/"+id).principal(new TestingAuthenticationToken("owner@test.com", "pass")))
            .andExpect(status().isNoContent());
        verify(service).delete("owner@test.com", id);
    }
}
