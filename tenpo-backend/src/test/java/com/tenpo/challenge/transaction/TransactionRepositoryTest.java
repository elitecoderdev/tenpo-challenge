package com.tenpo.challenge.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void shouldCountTransactionsByNormalizedCustomerName() {
        transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 1, 10, 0)));
        transactionRepository.save(buildTransaction("  CAMILA  TORRES ", "camila torres", LocalDateTime.of(2026, 3, 2, 10, 0)));
        transactionRepository.save(buildTransaction("Jose Perez", "jose perez", LocalDateTime.of(2026, 3, 3, 10, 0)));

        long count = transactionRepository.countByCustomerNameNormalized("camila torres");

        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldReturnTransactionsSortedByDateDescendingThenIdDescending() {
        Transaction oldest = transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 1, 10, 0)));
        Transaction newest = transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 3, 10, 0)));
        Transaction middle = transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 2, 10, 0)));

        List<Transaction> transactions = transactionRepository.findAllByOrderByTransactionDateDescIdDesc();

        assertThat(transactions).extracting(Transaction::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    private Transaction buildTransaction(String customerName, String normalizedCustomerName, LocalDateTime dateTime) {
        Transaction transaction = new Transaction();
        transaction.setAmountInPesos(5000);
        transaction.setMerchant("Merchant");
        transaction.setCustomerName(customerName);
        transaction.setCustomerNameNormalized(normalizedCustomerName);
        transaction.setTransactionDate(dateTime);
        return transaction;
    }
}
