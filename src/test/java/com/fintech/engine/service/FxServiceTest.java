package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.FxQuote;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.FxQuoteStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.FxQuoteRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.impl.FxServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FxServiceTest {

    @Mock
    private FxQuoteRepository fxQuoteRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LedgerService ledgerService;

    private FxServiceImpl fxService;

    private User testUser;
    private Account usdWallet;
    private Account eurWallet;

    @BeforeEach
    void setUp() {
        fxService = new FxServiceImpl(fxQuoteRepository, accountRepository, userRepository, ledgerService);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("alex@example.com");
        testUser.setFirstName("Alex");
        testUser.setLastName("Morgan");

        usdWallet = new Account(testUser, "USD-11223344", "USD Wallet", AccountType.LIABILITY, "USD");
        usdWallet.setId(UUID.randomUUID());
        usdWallet.setStatus(AccountStatus.ACTIVE);

        eurWallet = new Account(testUser, "EUR-55667788", "EUR Wallet", AccountType.LIABILITY, "EUR");
        eurWallet.setId(UUID.randomUUID());
        eurWallet.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should return live rates against base USD")
    void shouldReturnLiveRatesForBaseUsd() {
        List<FxRateResponse> rates = fxService.getLiveRates("USD");
        assertThat(rates).isNotEmpty();
        assertThat(rates).anyMatch(r -> r.targetCurrency().equals("EUR") && r.rate().compareTo(BigDecimal.ZERO) > 0);
        assertThat(rates).anyMatch(r -> r.targetCurrency().equals("GBP") && r.rate().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Should create guaranteed FX quote with 60s TTL")
    void shouldCreateQuoteSuccessfully() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(fxQuoteRepository.save(any(FxQuote.class))).thenAnswer(inv -> {
            FxQuote q = inv.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        FxQuoteRequest request = new FxQuoteRequest("USD", "EUR", new BigDecimal("100.00"));
        FxQuoteResponse response = fxService.createQuote("alex@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.sourceCurrency()).isEqualTo("USD");
        assertThat(response.targetCurrency()).isEqualTo("EUR");
        assertThat(response.sourceAmount()).isEqualByComparingTo("100.00");
        assertThat(response.targetAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.exchangeRate()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.expiresInSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("Should reject quote if source and target currency are the same")
    void shouldRejectQuoteForSameCurrency() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));

        FxQuoteRequest request = new FxQuoteRequest("USD", "USD", new BigDecimal("100.00"));
        assertThatThrownBy(() -> fxService.createQuote("alex@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source and target currencies must be distinct");
    }

    @Test
    @DisplayName("Should execute valid FX quote with dual ledger postings")
    void shouldExecuteFxQuoteSuccessfully() {
        UUID quoteId = UUID.randomUUID();
        FxQuote quote = new FxQuote();
        quote.setId(quoteId);
        quote.setUser(testUser);
        quote.setSourceCurrency("USD");
        quote.setTargetCurrency("EUR");
        quote.setSourceAmount(new BigDecimal("100.00"));
        quote.setTargetAmount(new BigDecimal("92.15"));
        quote.setExchangeRate(new BigDecimal("0.921500"));
        quote.setFee(new BigDecimal("0.50"));
        quote.setStatus(FxQuoteStatus.PENDING);
        quote.setExpiresAt(OffsetDateTime.now().plusSeconds(45));

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(fxQuoteRepository.findByIdAndUserId(quoteId, testUser.getId())).thenReturn(Optional.of(quote));
        when(accountRepository.findById(usdWallet.getId())).thenReturn(Optional.of(usdWallet));
        when(accountRepository.findById(eurWallet.getId())).thenReturn(Optional.of(eurWallet));

        // Source balance of $500.00 is sufficient for 100 + 0.50 fee
        when(ledgerService.getAccountBalance(usdWallet.getId())).thenReturn(new AccountBalanceResponse(
                usdWallet.getId(), usdWallet.getAccountNumber(), usdWallet.getAccountName(),
                usdWallet.getType(), "USD", usdWallet.getStatus(),
                new BigDecimal("500.00"), BigDecimal.ZERO, new BigDecimal("500.00")
        ));

        // Platform system accounts
        Account usdClearing = new Account(null, "SYS-FX-USD", "FX_CLEARING_USD", AccountType.ASSET, "USD");
        usdClearing.setId(UUID.randomUUID());
        Account eurClearing = new Account(null, "SYS-FX-EUR", "FX_CLEARING_EUR", AccountType.ASSET, "EUR");
        eurClearing.setId(UUID.randomUUID());
        Account feeRevenue = new Account(null, "SYS-4001-USD", "REVENUE_FEES", AccountType.REVENUE, "USD");
        feeRevenue.setId(UUID.randomUUID());

        when(accountRepository.findByAccountNumber("SYS-FX-USD")).thenReturn(Optional.of(usdClearing));
        when(accountRepository.findByAccountNumber("SYS-FX-EUR")).thenReturn(Optional.of(eurClearing));
        when(accountRepository.findByAccountNumber("SYS-4001-USD")).thenReturn(Optional.of(feeRevenue));

        ExecuteFxExchangeRequest request = new ExecuteFxExchangeRequest(quoteId, usdWallet.getId(), eurWallet.getId());
        FxExchangeResponse response = fxService.executeQuote("alex@example.com", request, "idemp-key-1");

        assertThat(response).isNotNull();
        assertThat(response.sourceAmount()).isEqualByComparingTo("100.00");
        assertThat(response.targetAmount()).isEqualByComparingTo("92.15");
        assertThat(response.sourceCurrency()).isEqualTo("USD");
        assertThat(response.targetCurrency()).isEqualTo("EUR");
        assertThat(quote.getStatus()).isEqualTo(FxQuoteStatus.EXECUTED);

        // Verify two balanced ledger posting legs were posted
        verify(ledgerService, times(2)).postTransaction(any(LedgerPostingRequest.class));
    }

    @Test
    @DisplayName("Should reject execution if quote has expired")
    void shouldRejectExpiredQuote() {
        UUID quoteId = UUID.randomUUID();
        FxQuote quote = new FxQuote();
        quote.setId(quoteId);
        quote.setUser(testUser);
        quote.setStatus(FxQuoteStatus.PENDING);
        quote.setExpiresAt(OffsetDateTime.now().minusSeconds(10)); // expired

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(fxQuoteRepository.findByIdAndUserId(quoteId, testUser.getId())).thenReturn(Optional.of(quote));

        ExecuteFxExchangeRequest request = new ExecuteFxExchangeRequest(quoteId, usdWallet.getId(), eurWallet.getId());
        assertThatThrownBy(() -> fxService.executeQuote("alex@example.com", request, null))
                .isInstanceOf(FxQuoteExpiredException.class);
    }
}

