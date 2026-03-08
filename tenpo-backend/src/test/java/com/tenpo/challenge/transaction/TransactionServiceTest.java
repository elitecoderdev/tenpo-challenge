package com.tenpo.challenge.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tenpo.challenge.shared.exception.BusinessRuleException;
import com.tenpo.challenge.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    private TransactionRequest transactionRequest;

    @BeforeEach
    void setUp() {
        transactionRequest = new TransactionRequest(
                20000,
                "  Supermercado Lider  ",
                "  Camila    Torres ",
                LocalDateTime.of(2026, 3, 7, 10, 30)
        );
    }

    @Test
    void shouldCreateTransactionWhenCustomerStillHasCapacity() {
        Transaction savedTransaction = new Transaction();
        savedTransaction.setAmountInPesos(Math.toIntExact(transactionRequest.amountInPesos()));
        savedTransaction.setMerchant("Supermercado Lider");
        savedTransaction.setCustomerName("Camila Torres");
        savedTransaction.setTransactionDate(transactionRequest.transactionDate());

        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(99L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toResponse(savedTransaction)).thenReturn(
                new TransactionResponse(1, 20000, "Supermercado Lider", "Camila Torres", transactionRequest.transactionDate())
        );

        TransactionResponse response = transactionService.createTransaction(transactionRequest);

        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction persistedTransaction = transactionCaptor.getValue();
        assertThat(persistedTransaction.getMerchant()).isEqualTo("Supermercado Lider");
        assertThat(persistedTransaction.getCustomerName()).isEqualTo("Camila Torres");
        assertThat(persistedTransaction.getAmountInPesos()).isEqualTo(20000);
        assertThat(response.id()).isEqualTo(1);
    }

    @Test
    void shouldRejectCreateWhenCustomerAlreadyHasOneHundredTransactions() {
        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(100L);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A Tenpista can only have up to 100 transactions.");

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldThrowWhenTransactionDoesNotExist() {
        when(transactionRepository.findById(44)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(44))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction with id 44 was not found.");
    }

    @Test
    void shouldFilterByCustomerName() {
        Transaction transaction = new Transaction();
        transaction.setAmountInPesos(1000);
        transaction.setMerchant("Farmacia");
        transaction.setCustomerName("Camila Torres");
        transaction.setTransactionDate(LocalDateTime.of(2026, 3, 6, 9, 0));

        TransactionResponse expected = new TransactionResponse(7, 1000, "Farmacia", "Camila Torres", transaction.getTransactionDate());
        when(transactionRepository.findAllByCustomerNameNormalizedOrderByTransactionDateDescIdDesc("camila torres"))
                .thenReturn(List.of(transaction));
        when(transactionMapper.toResponse(transaction)).thenReturn(expected);

        List<TransactionResponse> response = transactionService.listTransactions(" Camila   Torres ");

        assertThat(response).containsExactly(expected);
    }

    @Test
    void shouldConvertValidatedLongAmountToIntegerBeforeSaving() {
        TransactionRequest largeButValidRequest = new TransactionRequest(
                Integer.MAX_VALUE,
                "Supermercado Lider",
                "Camila Torres",
                LocalDateTime.of(2026, 3, 7, 10, 30)
        );

        Transaction savedTransaction = new Transaction();
        savedTransaction.setAmountInPesos(Integer.MAX_VALUE);
        savedTransaction.setMerchant("Supermercado Lider");
        savedTransaction.setCustomerName("Camila Torres");
        savedTransaction.setTransactionDate(largeButValidRequest.transactionDate());

        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(0L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toResponse(savedTransaction)).thenReturn(
                new TransactionResponse(1, Integer.MAX_VALUE, "Supermercado Lider", "Camila Torres", largeButValidRequest.transactionDate())
        );

        transactionService.createTransaction(largeButValidRequest);

        verify(transactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getAmountInPesos()).isEqualTo(Integer.MAX_VALUE);
    }
}
