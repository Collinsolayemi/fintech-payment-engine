package com.fintech.engine.service.impl;

import com.fintech.engine.dto.*;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.PaymentRequest;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.PaymentRequestStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.PaymentRequestRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.PaymentRequestService;
import com.fintech.engine.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestServiceImpl.class);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PaymentRequestRepository paymentRequestRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransferService transferService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentRequestServiceImpl(
            PaymentRequestRepository paymentRequestRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransferService transferService
    ) {
        this.paymentRequestRepository = paymentRequestRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transferService = transferService;
    }

    @Override
    public PaymentRequestResponse create(String userEmail, CreatePaymentRequestDto request) {
        User requester = resolveUser(userEmail);

        Account destination = accountRepository.findById(request.requesterAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.requesterAccountId()));
        if (destination.getUser() == null || !destination.getUser().getId().equals(requester.getId())) {
            throw new UnauthorizedAccountAccessException(destination.getId(), userEmail);
        }
        if (destination.getType() != AccountType.LIABILITY || destination.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination must be an ACTIVE customer wallet");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(request.amount());
        }

        User payerUser = null;
        String payerEmail = null;
        if (request.payerEmail() != null && !request.payerEmail().isBlank()) {
            payerEmail = request.payerEmail().trim().toLowerCase(Locale.ROOT);
            if (payerEmail.equalsIgnoreCase(requester.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request payment from yourself");
            }
            payerUser = userRepository.findByEmail(payerEmail).orElse(null);
        }

        if (request.expiresAt() != null && request.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiration time must be in the future");
        }

        PaymentRequest pr = new PaymentRequest();
        pr.setRequestCode(generateUniqueCode());
        pr.setRequesterUser(requester);
        pr.setRequesterAccount(destination);
        pr.setPayerUser(payerUser);
        pr.setPayerEmail(payerEmail);
        pr.setAmount(request.amount());
        pr.setCurrency(destination.getCurrency());
        pr.setDescription(request.description() != null && !request.description().isBlank()
                ? request.description().trim()
                : "Payment request from " + requester.getFirstName());
        pr.setStatus(PaymentRequestStatus.PENDING);
        pr.setExpiresAt(request.expiresAt());

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Created payment request {} ({} {}) by {}", saved.getRequestCode(), saved.getAmount(), saved.getCurrency(), userEmail);
        return PaymentRequestResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> listSent(String userEmail) {
        User user = resolveUser(userEmail);
        return paymentRequestRepository.findByRequesterUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(PaymentRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentRequestResponse> listIncoming(String userEmail) {
        User user = resolveUser(userEmail);
        return paymentRequestRepository.findByPayerUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(PaymentRequestResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentRequestResponse getByCode(String requestCode) {
        PaymentRequest pr = paymentRequestRepository.findByRequestCode(requestCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment request not found"));
        return PaymentRequestResponse.from(pr);
    }

    @Override
    public PaymentRequestResponse pay(String userEmail, UUID id, PayPaymentRequestDto request, String idempotencyKey) {
        User payer = resolveUser(userEmail);
        PaymentRequest pr = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment request not found"));

        if (pr.getRequesterUser().getId().equals(payer.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot pay your own payment request");
        }

        if (pr.getPayerUser() != null && !pr.getPayerUser().getId().equals(payer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This payment request is assigned to another user");
        }

        if (pr.getStatus() != PaymentRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment request is already " + pr.getStatus());
        }

        if (pr.getExpiresAt() != null && pr.getExpiresAt().isBefore(OffsetDateTime.now())) {
            pr.setStatus(PaymentRequestStatus.EXPIRED);
            paymentRequestRepository.save(pr);
            throw new ResponseStatusException(HttpStatus.GONE, "Payment request has expired");
        }

        Account payerAccount = accountRepository.findById(request.payerAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.payerAccountId()));
        if (payerAccount.getUser() == null || !payerAccount.getUser().getId().equals(payer.getId())) {
            throw new UnauthorizedAccountAccessException(payerAccount.getId(), userEmail);
        }

        String effectiveKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : "REQ-PAY-" + pr.getId();

        TransferResponse tx = transferService.transfer(
                new TransferRequest(
                        payerAccount.getId(),
                        pr.getRequesterAccount().getId(),
                        pr.getAmount(),
                        BigDecimal.ZERO,
                        pr.getCurrency(),
                        "Payment for request " + pr.getRequestCode() + ": " + pr.getDescription()
                ),
                effectiveKey,
                userEmail
        );

        pr.setPayerUser(payer);
        pr.setPayerEmail(payer.getEmail());
        pr.setStatus(PaymentRequestStatus.PAID);
        pr.setTransactionReference(tx.transactionReference());
        pr.setPaidAt(OffsetDateTime.now());

        PaymentRequest saved = paymentRequestRepository.save(pr);
        log.info("Payment request {} paid by {} (tx: {})", saved.getRequestCode(), userEmail, tx.transactionReference());
        return PaymentRequestResponse.from(saved);
    }

    @Override
    public PaymentRequestResponse decline(String userEmail, UUID id) {
        User payer = resolveUser(userEmail);
        PaymentRequest pr = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment request not found"));

        if (pr.getPayerUser() == null || !pr.getPayerUser().getId().equals(payer.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the designated payer can decline this request");
        }
        if (pr.getStatus() != PaymentRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING requests can be declined");
        }

        pr.setStatus(PaymentRequestStatus.DECLINED);
        return PaymentRequestResponse.from(paymentRequestRepository.save(pr));
    }

    @Override
    public PaymentRequestResponse cancel(String userEmail, UUID id) {
        User requester = resolveUser(userEmail);
        PaymentRequest pr = paymentRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment request not found"));

        if (!pr.getRequesterUser().getId().equals(requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the requester can cancel this request");
        }
        if (pr.getStatus() != PaymentRequestStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING requests can be cancelled");
        }

        pr.setStatus(PaymentRequestStatus.CANCELLED);
        return PaymentRequestResponse.from(paymentRequestRepository.save(pr));
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("REQ-");
            for (int i = 0; i < 8; i++) {
                sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
            }
            String candidate = sb.toString();
            if (!paymentRequestRepository.existsByRequestCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique payment request code");
    }
}

