package dev.kavrin.banking_ledger.reconciliation.application.service;

import dev.kavrin.banking_ledger.reconciliation.api.dto.ReconciliationResultSummaryResponse;
import dev.kavrin.banking_ledger.reconciliation.api.dto.SettlementBatchResponse;
import dev.kavrin.banking_ledger.reconciliation.application.query.GetSettlementBatchByIdQuery;
import dev.kavrin.banking_ledger.reconciliation.application.query.SearchReconciliationResultsQuery;
import dev.kavrin.banking_ledger.reconciliation.application.query.SearchSettlementBatchesQuery;
import dev.kavrin.banking_ledger.reconciliation.persistence.ReconciliationResultRepository;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchEntity;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementBatchRepository;
import dev.kavrin.banking_ledger.reconciliation.persistence.SettlementItemRepository;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ReconciliationQueryUseCase {

    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository itemRepository;
    private final ReconciliationResultRepository resultRepository;
    private final ReconciliationResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public SettlementBatchResponse getById(GetSettlementBatchByIdQuery query) {
        var batch = batchRepository.findById(query.batchId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                        "Settlement batch not found: " + query.batchId(),
                        "Settlement batch not found."
                ));
        var items = itemRepository.findByBatch_IdOrderByExternalTransactionReferenceAscIdAsc(batch.getId());
        var results = resultRepository.findByBatch_IdOrderByCreatedAtDescIdDesc(
                batch.getId(),
                PageRequest.of(0, Integer.MAX_VALUE)
        ).getContent();
        return responseMapper.toResponse(batch, items, results);
    }

    @Transactional(readOnly = true)
    public Page<SettlementBatchResponse> searchBatches(SearchSettlementBatchesQuery query) {
        var pageable = PageRequest.of(
                Math.max(0, query.page()),
                Math.max(1, query.size()),
                Sort.by(Sort.Direction.DESC, "importedAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        return batchRepository.findAll((root, criteriaQuery, criteriaBuilder) -> {
            if (query.status() == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), query.status());
        }, pageable).map(this::summaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationResultSummaryResponse> searchResults(SearchReconciliationResultsQuery query) {
        if (!batchRepository.existsById(query.batchId())) {
            throw new ResourceNotFoundException(
                    ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                    "Settlement batch not found: " + query.batchId(),
                    "Settlement batch not found."
            );
        }
        var pageable = PageRequest.of(
                Math.max(0, query.page()),
                Math.max(1, query.size()),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        return resultRepository.findAll((root, criteriaQuery, criteriaBuilder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteriaBuilder.equal(root.get("batch").get("id"), query.batchId()));
            if (query.mismatchType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("mismatchType"), query.mismatchType()));
            }
            if (query.severity() != null) {
                predicates.add(criteriaBuilder.equal(root.get("severity"), query.severity()));
            }
            if (query.status() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), query.status()));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        }, pageable).map(responseMapper::toResultResponse);
    }

    private SettlementBatchResponse summaryResponse(SettlementBatchEntity batch) {
        return responseMapper.toResponse(batch, java.util.List.of(), java.util.List.of());
    }
}
