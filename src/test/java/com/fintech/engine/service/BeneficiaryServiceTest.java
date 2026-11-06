package com.fintech.engine.service;

import com.fintech.engine.dto.*;
import com.fintech.engine.model.entity.*;
import com.fintech.engine.model.enums.*;
import com.fintech.engine.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {
    @Mock BeneficiaryRepository beneficiaries;
    @Mock UserRepository users;
    @Mock AccountRepository accounts;
    BeneficiaryService service;
    User owner;
    @BeforeEach void setup() {
        service = new BeneficiaryService(beneficiaries, users, accounts);
        owner = new User(); owner.setId(UUID.randomUUID());
        when(users.findByEmail("owner@test.com")).thenReturn(Optional.of(owner));
    }
    CreateBeneficiaryRequest bank() {
        return new CreateBeneficiaryRequest(BeneficiaryType.BANK, " Rent ", null, " abc ", "00123456", " Alex ", "usd");
    }
    void status(Runnable action, int expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
            e -> assertThat(e.getStatusCode().value()).isEqualTo(expected));
    }
    @Test void normalizesBankDestinationAndPreservesLeadingZeros() {
        when(beneficiaries.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        BeneficiaryResponse response = service.create("owner@test.com", bank());
        assertThat(response.nickname()).isEqualTo("Rent");
        assertThat(response.bankCode()).isEqualTo("ABC");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.accountNumber()).isEqualTo("00123456");
        verify(beneficiaries).existsByUserIdAndDestinationKey(owner.getId(), "BANK:USD:ABC:00123456");
    }
    @Test void rejectsDuplicate() {
        when(beneficiaries.existsByUserIdAndDestinationKey(any(), any())).thenReturn(true);
        status(() -> service.create("owner@test.com", bank()), 409);
        verify(beneficiaries, never()).saveAndFlush(any());
    }
    @Test void handlesConcurrentDuplicate() {
        when(beneficiaries.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        status(() -> service.create("owner@test.com", bank()), 409);
    }
    @Test void preventsAccessToAnotherOwnersBeneficiary() {
        UUID id = UUID.randomUUID();
        status(() -> service.rename("owner@test.com", id, new RenameBeneficiaryRequest("New")), 404);
        status(() -> service.delete("owner@test.com", id), 404);
        verify(beneficiaries, never()).delete(any());
        verify(beneficiaries, never()).save(any());
    }
    @Test void listsOnlyCurrentOwnersRecipients() {
        when(beneficiaries.findByUserIdOrderByCreatedAtDesc(owner.getId())).thenReturn(List.of());
        assertThat(service.list("owner@test.com")).isEmpty();
        verify(beneficiaries).findByUserIdOrderByCreatedAtDesc(owner.getId());
    }
    @Test void savesActiveExternalWallet() {
        User recipient = new User(); recipient.setId(UUID.randomUUID());
        Account wallet = new Account(recipient, "W1", "Alex", AccountType.LIABILITY, "USD");
        wallet.setId(UUID.randomUUID());
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(beneficiaries.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var response = service.create("owner@test.com", new CreateBeneficiaryRequest(BeneficiaryType.WALLET, "Alex", wallet.getId(), null, null, null, "USD"));
        assertThat(response.recipientAccountId()).isEqualTo(wallet.getId());
        assertThat(response.accountName()).isEqualTo("Alex");
    }
    @Test void rejectsOwnWalletAndInactiveWallet() {
        Account wallet = new Account(owner, "W1", "Me", AccountType.LIABILITY, "USD"); wallet.setId(UUID.randomUUID());
        when(accounts.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        var request = new CreateBeneficiaryRequest(BeneficiaryType.WALLET, "Me", wallet.getId(), null, null, null, "USD");
        status(() -> service.create("owner@test.com", request), 400);
        wallet.setStatus(AccountStatus.FROZEN);
        status(() -> service.create("owner@test.com", request), 400);
    }
    @Test void rejectsMissingBankFields() {
        status(() -> service.create("owner@test.com", new CreateBeneficiaryRequest(BeneficiaryType.BANK, "Alex", null, "abc", "", "Alex", "USD")), 400);
    }
    @Test void renamesAndDeletesOwnedRecipient() {
        Beneficiary b = new Beneficiary(); b.setId(UUID.randomUUID()); b.setUserId(owner.getId());
        when(beneficiaries.findByIdAndUserId(b.getId(), owner.getId())).thenReturn(Optional.of(b));
        when(beneficiaries.save(b)).thenReturn(b);
        assertThat(service.rename("owner@test.com", b.getId(), new RenameBeneficiaryRequest(" New ")).nickname()).isEqualTo("New");
        service.delete("owner@test.com", b.getId()); verify(beneficiaries).delete(b);
    }
}
