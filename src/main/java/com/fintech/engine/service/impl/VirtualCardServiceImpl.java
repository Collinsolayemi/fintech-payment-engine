package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.entity.VirtualCard;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.CardBrand;
import com.fintech.engine.model.enums.CardStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.repository.VirtualCardRepository;
import com.fintech.engine.service.VirtualCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class VirtualCardServiceImpl implements VirtualCardService {

    private static final Logger log = LoggerFactory.getLogger(VirtualCardServiceImpl.class);

    private final VirtualCardRepository virtualCardRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public VirtualCardServiceImpl(
            VirtualCardRepository virtualCardRepository,
            AccountRepository accountRepository,
            UserRepository userRepository) {
        this.virtualCardRepository = virtualCardRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public VirtualCardResponse issueCard(String userEmailOrId, IssueCardRequest request) {
        User user = resolveUser(userEmailOrId);

        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException("Account not found with ID: " + request.accountId()));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccountAccessException(account.getId(), user.getEmail());
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Cannot issue a card on an inactive account (" + account.getStatus() + ")");
        }

        CardBrand brand = request.cardBrand() != null ? request.cardBrand() : CardBrand.VISA;
        String cardNumber = generateLuhnCardNumber(brand);
        String lastFour = cardNumber.substring(cardNumber.length() - 4);
        String cvv = String.format("%03d", secureRandom.nextInt(1000));

        LocalDate now = LocalDate.now();
        int expiryMonth = now.getMonthValue();
        int expiryYear = now.getYear() + 3;

        BigDecimal limit = request.monthlyLimit() != null && request.monthlyLimit().compareTo(BigDecimal.ZERO) > 0
                ? request.monthlyLimit()
                : new BigDecimal("5000.00");

        VirtualCard card = new VirtualCard();
        card.setUser(user);
        card.setAccount(account);
        card.setCardholderName(request.cardholderName() != null && !request.cardholderName().isBlank()
                ? request.cardholderName().trim()
                : user.getFirstName() + " " + user.getLastName());
        card.setCardNumber(cardNumber);
        card.setLastFour(lastFour);
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);
        card.setCvv(cvv);
        card.setStatus(CardStatus.ACTIVE);
        card.setCardBrand(brand);
        card.setMonthlyLimit(limit);
        card.setOnlinePaymentsEnabled(true);
        card.setInternationalEnabled(true);

        card = virtualCardRepository.save(card);

        log.info("Issued virtual {} card ending in {} for user {}",
                brand, lastFour, user.getEmail());

        return mapToResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VirtualCardResponse> getUserCards(String userEmailOrId) {
        User user = resolveUser(userEmailOrId);
        return virtualCardRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CardDetailsResponse getCardDetails(String userEmailOrId, UUID cardId) {
        User user = resolveUser(userEmailOrId);
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new VirtualCardNotFoundException(cardId));

        return new CardDetailsResponse(
                card.getId(),
                card.getCardNumber(),
                card.getCvv(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getCardholderName()
        );
    }

    @Override
    @Transactional
    public VirtualCardResponse updateCardStatus(String userEmailOrId, UUID cardId, UpdateCardStatusRequest request) {
        User user = resolveUser(userEmailOrId);
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new VirtualCardNotFoundException(cardId));

        card.setStatus(request.status());
        card = virtualCardRepository.save(card);

        log.info("Updated status of virtual card {} to {}", cardId, request.status());
        return mapToResponse(card);
    }

    @Override
    @Transactional
    public VirtualCardResponse updateCardControls(String userEmailOrId, UUID cardId, UpdateCardControlsRequest request) {
        User user = resolveUser(userEmailOrId);
        VirtualCard card = virtualCardRepository.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new VirtualCardNotFoundException(cardId));

        if (request.monthlyLimit() != null && request.monthlyLimit().compareTo(BigDecimal.ZERO) > 0) {
            card.setMonthlyLimit(request.monthlyLimit());
        }
        if (request.onlinePaymentsEnabled() != null) {
            card.setOnlinePaymentsEnabled(request.onlinePaymentsEnabled());
        }
        if (request.internationalEnabled() != null) {
            card.setInternationalEnabled(request.internationalEnabled());
        }

        card = virtualCardRepository.save(card);
        log.info("Updated controls of virtual card {}", cardId);
        return mapToResponse(card);
    }

    private User resolveUser(String userEmailOrId) {
        try {
            UUID userId = UUID.fromString(userEmailOrId);
            return userRepository.findById(userId)
                    .orElseGet(() -> userRepository.findByEmail(userEmailOrId)
                            .orElseThrow(() -> new UserNotFoundException(userEmailOrId)));
        } catch (IllegalArgumentException notUuid) {
            return userRepository.findByEmail(userEmailOrId)
                    .orElseThrow(() -> new UserNotFoundException(userEmailOrId));
        }
    }

    private VirtualCardResponse mapToResponse(VirtualCard card) {
        String maskedNumber = "•••• •••• •••• " + card.getLastFour();
        return new VirtualCardResponse(
                card.getId(),
                card.getAccount().getId(),
                card.getAccount().getAccountNumber(),
                card.getAccount().getCurrency(),
                card.getCardholderName(),
                maskedNumber,
                card.getLastFour(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getStatus(),
                card.getCardBrand(),
                card.getCardType(),
                card.getMonthlyLimit(),
                card.isOnlinePaymentsEnabled(),
                card.isInternationalEnabled(),
                card.getCreatedAt()
        );
    }

    private String generateLuhnCardNumber(CardBrand brand) {
        String bin = brand == CardBrand.VISA ? "4532" : "5399";
        StringBuilder sb = new StringBuilder(bin);
        // Add 11 random digits (making 15 digits total)
        for (int i = 0; i < 11; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        // Calculate Luhn checksum for 16th digit
        int checkDigit = calculateLuhnCheckDigit(sb.toString());
        sb.append(checkDigit);
        return sb.toString();
    }

    private int calculateLuhnCheckDigit(String partialNumber) {
        int sum = 0;
        boolean alternate = true;
        for (int i = partialNumber.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(partialNumber.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum * 9) % 10;
    }
}

