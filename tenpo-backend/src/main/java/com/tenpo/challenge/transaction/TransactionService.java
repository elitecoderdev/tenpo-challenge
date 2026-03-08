package com.tenpo.challenge.transaction;

import com.tenpo.challenge.shared.exception.BusinessRuleException;
import com.tenpo.challenge.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final int MAX_TRANSACTIONS_PER_CUSTOMER = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(String customerName) {
        List<Transaction> transactions = hasText(customerName)
                ? transactionRepository.findAllByCustomerNameNormalizedOrderByTransactionDateDescIdDesc(
                        Transaction.canonicalize(customerName))
                : transactionRepository.findAllByOrderByTransactionDateDescIdDesc();

        return transactions.stream().map(transactionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Integer transactionId) {
        return transactionMapper.toResponse(findTransaction(transactionId));
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        String sanitizedCustomerName = sanitizeText(request.customerName());
        ensureTransactionQuota(sanitizedCustomerName, null);

        Transaction transaction = new Transaction();
        applyRequest(transaction, request, sanitizedCustomerName);

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse updateTransaction(Integer transactionId, TransactionRequest request) {
        Transaction transaction = findTransaction(transactionId);
        String sanitizedCustomerName = sanitizeText(request.customerName());
        ensureTransactionQuota(sanitizedCustomerName, transaction);

        applyRequest(transaction, request, sanitizedCustomerName);
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void deleteTransaction(Integer transactionId) {
        Transaction transaction = findTransaction(transactionId);
        transactionRepository.delete(transaction);
    }

    private Transaction findTransaction(Integer transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction with id " + transactionId + " was not found."));
    }

    private void applyRequest(Transaction transaction, TransactionRequest request, String sanitizedCustomerName) {
        transaction.setAmountInPesos(Math.toIntExact(request.amountInPesos()));
        transaction.setMerchant(sanitizeText(request.merchant()));
        transaction.setCustomerName(sanitizedCustomerName);
        transaction.setTransactionDate(request.transactionDate());
    }

    private void ensureTransactionQuota(String customerName, Transaction existingTransaction) {
        String normalizedCustomerName = Transaction.canonicalize(customerName);
        boolean customerChanged = existingTransaction == null
                || !normalizedCustomerName.equals(existingTransaction.getCustomerNameNormalized());

        if (!customerChanged) {
            return;
        }

        long customerTransactions = transactionRepository.countByCustomerNameNormalized(normalizedCustomerName);
        if (customerTransactions >= MAX_TRANSACTIONS_PER_CUSTOMER) {
            throw new BusinessRuleException(
                    "A Tenpista can only have up to 100 transactions.",
                    HttpStatus.CONFLICT
            );
        }
    }

    // English: Normalize whitespace before persisting so search, validation and UI stay predictable.
    // Espanol: Normalizamos los espacios antes de persistir para que la busqueda, la validacion y la UI sean predecibles.
    private String sanitizeText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
