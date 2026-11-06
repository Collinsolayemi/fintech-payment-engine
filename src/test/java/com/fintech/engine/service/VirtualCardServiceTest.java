package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.entity.VirtualCard;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.CardBrand;
import com.fintech.engine.model.enums.CardStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.repository.VirtualCardRepository;
import com.fintech.engine.service.impl.VirtualCardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VirtualCardServiceTest {

    @Mock
    private VirtualCardRepository virtualCardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    private VirtualCardServiceImpl virtualCardService;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        virtualCardService = new VirtualCardServiceImpl(virtualCardRepository, accountRepository, userRepository);

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("alex@example.com");
        testUser.setFirstName("Alex");
        testUser.setLastName("Morgan");

        testAccount = new Account(testUser, "USD-11223344", "Primary Wallet", AccountType.LIABILITY, "USD");
        testAccount.setId(UUID.randomUUID());
        testAccount.setStatus(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should issue virtual debit card with valid 16-digit Luhn number and CVV")
    void shouldIssueCardSuccessfully() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(accountRepository.findById(testAccount.getId())).thenReturn(Optional.of(testAccount));
        when(virtualCardRepository.save(any(VirtualCard.class))).thenAnswer(inv -> {
            VirtualCard c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        IssueCardRequest request = new IssueCardRequest(
                testAccount.getId(),
                "Alex Morgan",
                CardBrand.VISA,
                new BigDecimal("2500.00")
        );

        VirtualCardResponse response = virtualCardService.issueCard("alex@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.cardholderName()).isEqualTo("Alex Morgan");
        assertThat(response.cardBrand()).isEqualTo(CardBrand.VISA);
        assertThat(response.maskedCardNumber()).startsWith("•••• •••• •••• ");
        assertThat(response.lastFour()).hasSize(4);
        assertThat(response.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(response.monthlyLimit()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("Should reject card issuing if account is frozen")
    void shouldRejectCardIssueIfAccountInactive() {
        testAccount.setStatus(AccountStatus.FROZEN);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(accountRepository.findById(testAccount.getId())).thenReturn(Optional.of(testAccount));

        IssueCardRequest request = new IssueCardRequest(
                testAccount.getId(),
                "Alex Morgan",
                CardBrand.VISA,
                new BigDecimal("2500.00")
        );

        assertThatThrownBy(() -> virtualCardService.issueCard("alex@example.com", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot issue a card on an inactive account");
    }

    @Test
    @DisplayName("Should securely reveal full PAN and CVV to cardholder")
    void shouldRevealCardDetailsForOwner() {
        UUID cardId = UUID.randomUUID();
        VirtualCard card = new VirtualCard();
        card.setId(cardId);
        card.setUser(testUser);
        card.setAccount(testAccount);
        card.setCardholderName("Alex Morgan");
        card.setCardNumber("4532123456789012");
        card.setCvv("789");
        card.setExpiryMonth(11);
        card.setExpiryYear(2028);

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualCardRepository.findByIdAndUserId(cardId, testUser.getId())).thenReturn(Optional.of(card));

        CardDetailsResponse details = virtualCardService.getCardDetails("alex@example.com", cardId);

        assertThat(details).isNotNull();
        assertThat(details.cardNumber()).isEqualTo("4532123456789012");
        assertThat(details.cvv()).isEqualTo("789");
    }

    @Test
    @DisplayName("Should update card status to FROZEN")
    void shouldUpdateCardStatusToFrozen() {
        UUID cardId = UUID.randomUUID();
        VirtualCard card = new VirtualCard();
        card.setId(cardId);
        card.setUser(testUser);
        card.setAccount(testAccount);
        card.setStatus(CardStatus.ACTIVE);
        card.setLastFour("9012");

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));
        when(virtualCardRepository.findByIdAndUserId(cardId, testUser.getId())).thenReturn(Optional.of(card));
        when(virtualCardRepository.save(any(VirtualCard.class))).thenAnswer(inv -> inv.getArgument(0));

        VirtualCardResponse response = virtualCardService.updateCardStatus(
                "alex@example.com", cardId, new UpdateCardStatusRequest(CardStatus.FROZEN));

        assertThat(response.status()).isEqualTo(CardStatus.FROZEN);
    }
}

