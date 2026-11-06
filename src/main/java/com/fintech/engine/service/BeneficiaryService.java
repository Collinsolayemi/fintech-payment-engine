package com.fintech.engine.service;
import com.fintech.engine.dto.*;
import com.fintech.engine.model.entity.*;
import com.fintech.engine.model.enums.*;
import com.fintech.engine.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
@Service
@Transactional
public class BeneficiaryService {
    private final BeneficiaryRepository beneficiaries;
    private final UserRepository users;
    private final AccountRepository accounts;
    public BeneficiaryService(BeneficiaryRepository beneficiaries, UserRepository users, AccountRepository accounts) {
        this.beneficiaries = beneficiaries; this.users = users; this.accounts = accounts;
    }
    private UUID owner(String email) {
        return users.findByEmail(email).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "User not found")).getId();
    }
    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
    @Transactional(readOnly=true)
    public List<BeneficiaryResponse> list(String email) {
        return beneficiaries.findByUserIdOrderByCreatedAtDesc(owner(email)).stream().map(BeneficiaryResponse::from).toList();
    }
    public BeneficiaryResponse create(String email, CreateBeneficiaryRequest request) {
        UUID userId = owner(email);
        Beneficiary b = new Beneficiary();
        b.setUserId(userId); b.setType(request.type()); b.setNickname(request.nickname().strip());
        b.setCurrency(request.currency().toUpperCase(Locale.ROOT));
        if (request.type() == BeneficiaryType.WALLET) {
            if (request.recipientAccountId() == null || request.bankCode() != null || request.accountNumber() != null || request.accountName() != null)
                throw error(HttpStatus.BAD_REQUEST, "Wallet beneficiaries require only a recipient account ID");
            Account account = accounts.findById(request.recipientAccountId()).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Recipient wallet not found"));
            if (!account.isActive() || account.getUser() == null || account.getType() != AccountType.LIABILITY)
                throw error(HttpStatus.BAD_REQUEST, "Recipient must be an active customer wallet");
            if (userId.equals(account.getUser().getId())) throw error(HttpStatus.BAD_REQUEST, "Cannot save your own wallet as a beneficiary");
            if (!b.getCurrency().equals(account.getCurrency())) throw error(HttpStatus.BAD_REQUEST, "Recipient currency does not match");
            b.setRecipientAccountId(account.getId()); b.setAccountName(account.getAccountName());
            b.setDestinationKey("WALLET:" + account.getId());
        } else {
            if (request.recipientAccountId() != null || blank(request.bankCode()) || blank(request.accountNumber()) || blank(request.accountName()))
                throw error(HttpStatus.BAD_REQUEST, "Bank beneficiaries require bank code, account number and account name");
            String code = request.bankCode().strip(); String number = request.accountNumber().strip();
            if (!code.matches("[A-Za-z0-9-]{1,32}") || !number.matches("[0-9]{4,64}"))
                throw error(HttpStatus.BAD_REQUEST, "Invalid bank code or account number");
            b.setBankCode(code.toUpperCase(Locale.ROOT)); b.setAccountNumber(number); b.setAccountName(request.accountName().strip());
            b.setDestinationKey("BANK:" + b.getCurrency() + ":" + b.getBankCode() + ":" + number);
        }
        if (beneficiaries.existsByUserIdAndDestinationKey(userId, b.getDestinationKey()))
            throw error(HttpStatus.CONFLICT, "Beneficiary already saved");
        try { return BeneficiaryResponse.from(beneficiaries.saveAndFlush(b)); }
        catch (DataIntegrityViolationException e) { throw error(HttpStatus.CONFLICT, "Beneficiary already saved"); }
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private Beneficiary owned(String email, UUID id) {
        return beneficiaries.findByIdAndUserId(id, owner(email)).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Beneficiary not found"));
    }
    public BeneficiaryResponse rename(String email, UUID id, RenameBeneficiaryRequest request) {
        Beneficiary b = owned(email, id); b.setNickname(request.nickname().strip());
        return BeneficiaryResponse.from(beneficiaries.save(b));
    }
    public void delete(String email, UUID id) { beneficiaries.delete(owned(email, id)); }
}
