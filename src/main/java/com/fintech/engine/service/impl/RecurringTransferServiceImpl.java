package com.fintech.engine.service.impl;

import com.fintech.engine.dto.CreateRecurringTransferRequest;
import com.fintech.engine.dto.RecurringTransferResponse;
import com.fintech.engine.dto.TransferRequest;
import com.fintech.engine.dto.TransferResponse;
import com.fintech.engine.exception.*;
import com.fintech.engine.model.entity.Account;
import com.fintech.engine.model.entity.RecurringTransfer;
import com.fintech.engine.model.entity.User;
import com.fintech.engine.model.enums.AccountStatus;
import com.fintech.engine.model.enums.AccountType;
import com.fintech.engine.model.enums.RecurrenceFrequency;
import com.fintech.engine.model.enums.RecurringTransferStatus;
import com.fintech.engine.repository.AccountRepository;
import com.fintech.engine.repository.RecurringTransferRepository;
import com.fintech.engine.repository.UserRepository;
import com.fintech.engine.service.RecurringTransferService;
import com.fintech.engine.service.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RecurringTransferServiceImpl implements RecurringTransferService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransferServiceImpl.class);

    private final RecurringTransferRepository recurringTransferRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransferService transferService;

    public RecurringTransferServiceImpl(
            RecurringTransferRepository recurringTransferRepository,
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransferService transferService
    ) {
        this.recurringTransferRepository = recurringTransferRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transferService = transferService;
    }

    @Override
    public RecurringTransferResponse create(String userEmail, CreateRecurringTransferRequest request) {
        User user = resolveUser(userEmail);

        if (request.senderAccountId().equals(request.recipientAccountId())) {
            throw new SelfTransferException(request.senderAccountId());
        }

        Account sender = accountRepository.findById(request.senderAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.senderAccountId()));
        if (sender.getUser() == null || !sender.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccountAccessException(sender.getId(), userEmail);
        }
        if (sender.getType() != AccountType.LIABILITY || sender.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sender must be an ACTIVE customer wallet");
        }

        Account recipient = accountRepository.findById(request.recipientAccountId())
                .orElseThrow(() -> new AccountNotFoundException(request.recipientAccountId()));
        if (recipient.getType() != AccountType.LIABILITY || recipient.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient must be an ACTIVE customer wallet");
        }

        if (!sender.getCurrency().equalsIgnoreCase(recipient.getCurrency())) {
            throw new CurrencyMismatchException(sender.getCurrency(), recipient.getCurrency());
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException(request.amount());
        }

        OffsetDateTime nextRunAt = request.startAt() != null
                ? request.startAt()
                : computeNextRun(OffsetDateTime.now(), request.frequency());

        if (request.endDate() != null && request.endDate().isBefore(nextRunAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date cannot be earlier than first scheduled run");
        }

        RecurringTransfer rt = new RecurringTransfer();
        rt.setUser(user);
        rt.setSenderAccount(sender);
        rt.setRecipientAccount(recipient);
        rt.setAmount(request.amount());
        rt.setCurrency(sender.getCurrency());
        rt.setDescription(request.description() != null && !request.description().isBlank()
                ? request.description().trim()
                : "Recurring transfer (" + request.frequency() + ")");
        rt.setFrequency(request.frequency());
        rt.setStatus(RecurringTransferStatus.ACTIVE);
        rt.setNextRunAt(nextRunAt);
        rt.setEndDate(request.endDate());

        RecurringTransfer saved = recurringTransferRepository.save(rt);
        log.info("Created {} recurring transfer {} for user {} ({} {})",
                saved.getFrequency(), saved.getId(), userEmail, saved.getAmount(), saved.getCurrency());
        return RecurringTransferResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecurringTransferResponse> list(String userEmail) {
        User user = resolveUser(userEmail);
        return recurringTransferRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(RecurringTransferResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecurringTransferResponse getById(String userEmail, UUID id) {
        return RecurringTransferResponse.from(resolveOwned(userEmail, id));
    }

    @Override
    public RecurringTransferResponse pause(String userEmail, UUID id) {
        RecurringTransfer rt = resolveOwned(userEmail, id);
        if (rt.getStatus() != RecurringTransferStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only ACTIVE standing orders can be paused");
        }
        rt.setStatus(RecurringTransferStatus.PAUSED);
        return RecurringTransferResponse.from(recurringTransferRepository.save(rt));
    }

    @Override
    public RecurringTransferResponse resume(String userEmail, UUID id) {
        RecurringTransfer rt = resolveOwned(userEmail, id);
        if (rt.getStatus() != RecurringTransferStatus.PAUSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PAUSED standing orders can be resumed");
        }
        rt.setStatus(RecurringTransferStatus.ACTIVE);
        if (rt.getNextRunAt().isBefore(OffsetDateTime.now())) {
            rt.setNextRunAt(computeNextRun(OffsetDateTime.now(), rt.getFrequency()));
        }
        return RecurringTransferResponse.from(recurringTransferRepository.save(rt));
    }

    @Override
    public RecurringTransferResponse cancel(String userEmail, UUID id) {
        RecurringTransfer rt = resolveOwned(userEmail, id);
        if (rt.getStatus() == RecurringTransferStatus.COMPLETED || rt.getStatus() == RecurringTransferStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Standing order is already " + rt.getStatus());
        }
        rt.setStatus(RecurringTransferStatus.CANCELLED);
        return RecurringTransferResponse.from(recurringTransferRepository.save(rt));
    }

    @Override
    public RecurringTransferResponse executeNow(String userEmail, UUID id) {
        RecurringTransfer rt = resolveOwned(userEmail, id);
        if (rt.getStatus() != RecurringTransferStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Standing order must be ACTIVE to execute");
        }
        executeSingle(rt, userEmail);
        return RecurringTransferResponse.from(recurringTransferRepository.save(rt));
    }

    @Override
    @Scheduled(fixedDelayString = "${fintech.recurring.poll-interval-ms:60000}")
    public void processDueTransfers() {
        List<RecurringTransfer> due = recurringTransferRepository.findByStatusAndNextRunAtLessThanEqual(
                RecurringTransferStatus.ACTIVE,
                OffsetDateTime.now()
        );
        if (due.isEmpty()) {
            return;
        }
        log.info("Processing {} due recurring transfer(s)", due.size());
        for (RecurringTransfer rt : due) {
            try {
                executeSingle(rt, rt.getUser().getEmail());
            } catch (Exception ex) {
                log.warn("Scheduled recurring transfer {} failed: {}", rt.getId(), ex.getMessage());
                rt.setLastRunAt(OffsetDateTime.now());
                rt.setLastFailureReason(ex.getMessage());
                rt.setNextRunAt(computeNextRun(OffsetDateTime.now(), rt.getFrequency()));
            }
            recurringTransferRepository.save(rt);
        }
    }

    private void executeSingle(RecurringTransfer rt, String actorEmail) {
        String idempotencyKey = "REC-" + rt.getId() + "-" + (rt.getExecutionCount() + 1);
        TransferRequest transferRequest = new TransferRequest(
                rt.getSenderAccount().getId(),
                rt.getRecipientAccount().getId(),
                rt.getAmount(),
                BigDecimal.ZERO,
                rt.getCurrency(),
                rt.getDescription()
        );

        TransferResponse tx = transferService.transfer(transferRequest, idempotencyKey, actorEmail);

        OffsetDateTime now = OffsetDateTime.now();
        rt.setExecutionCount(rt.getExecutionCount() + 1);
        rt.setLastRunAt(now);
        rt.setLastTransactionReference(tx.transactionReference());
        rt.setLastFailureReason(null);

        OffsetDateTime next = computeNextRun(now, rt.getFrequency());
        rt.setNextRunAt(next);
        if (rt.getEndDate() != null && next.isAfter(rt.getEndDate())) {
            rt.setStatus(RecurringTransferStatus.COMPLETED);
        }
    }

    private OffsetDateTime computeNextRun(OffsetDateTime from, RecurrenceFrequency frequency) {
        return switch (frequency) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case BIWEEKLY -> from.plusWeeks(2);
            case MONTHLY -> from.plusMonths(1);
        };
    }

    private User resolveUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    private RecurringTransfer resolveOwned(String userEmail, UUID id) {
        User user = resolveUser(userEmail);
        RecurringTransfer rt = recurringTransferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurring transfer not found"));
        if (!rt.getUser().getId().equals(user.getId())) {
            throw new UnauthorizedAccountAccessException(rt.getSenderAccount().getId(), userEmail);
        }
        return rt;
    }
}
