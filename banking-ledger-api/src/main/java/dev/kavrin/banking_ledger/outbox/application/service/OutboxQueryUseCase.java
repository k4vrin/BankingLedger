package dev.kavrin.banking_ledger.outbox.application.service;

import dev.kavrin.banking_ledger.outbox.api.dto.OutboxEventResponse;
import dev.kavrin.banking_ledger.outbox.domain.model.OutboxStatus;
import dev.kavrin.banking_ledger.outbox.persistence.OutboxEventRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxQueryUseCase {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxResponseMapper mapper;

    @Transactional(readOnly = true)
    public OutboxEventResponse getById(UUID eventId) {
        return outboxEventRepository.findById(eventId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Outbox event not found: " + eventId,
                        "Outbox event not found."
                ));
    }

    @Transactional(readOnly = true)
    public Page<OutboxEventResponse> searchByStatus(OutboxStatus status, int page, int size) {
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        return outboxEventRepository.findByStatusOrderByCreatedAtDescIdDesc(status, pageable)
                .map(mapper::toResponse);
    }
}
