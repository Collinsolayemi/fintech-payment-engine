package com.fintech.engine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.engine.dto.AccountBalanceResponse;
import com.fintech.engine.dto.CreateAccountRequest;
import com.fintech.engine.exception.AccountNotFoundException;
import com.fintech.engine.exception.GlobalExceptionHandler;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AccountController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    @Test
    @DisplayName("POST /api/v1/accounts - Should create account and return 201 Created")
    void shouldCreateAccount() throws Exception {
        UUID accountId = UUID.randomUUID();
        CreateAccountRequest request = new CreateAccountRequest(
                null, "Personal Wallet", AccountType.LIABILITY, "USD"
        );

        AccountBalanceResponse response = new AccountBalanceResponse(
                accountId, "USD-98765432", "Personal Wallet", AccountType.LIABILITY,
                "USD", AccountStatus.ACTIVE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.accountNumber").value("USD-98765432"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Should return account balance")
    void shouldReturnAccountBalance() throws Exception {
        UUID accountId = UUID.randomUUID();
        AccountBalanceResponse response = new AccountBalanceResponse(
                accountId, "USD-98765432", "Personal Wallet", AccountType.LIABILITY,
                "USD", AccountStatus.ACTIVE, new BigDecimal("250.00"), BigDecimal.ZERO, new BigDecimal("250.00")
        );

        when(accountService.getAccount(accountId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/" + accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} - Should return 404 when account does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        UUID randomId = UUID.randomUUID();
        when(accountService.getAccount(randomId)).thenThrow(new AccountNotFoundException(randomId));

        mockMvc.perform(get("/api/v1/accounts/" + randomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/user/{userId} - Should return user accounts list")
    void shouldReturnUserAccountsList() throws Exception {
        UUID userId = UUID.randomUUID();
        AccountBalanceResponse acc = new AccountBalanceResponse(
                UUID.randomUUID(), "USD-1111", "Wallet", AccountType.LIABILITY,
                "USD", AccountStatus.ACTIVE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN
        );

        when(accountService.getUserAccounts(userId)).thenReturn(List.of(acc));

        mockMvc.perform(get("/api/v1/accounts/user/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
