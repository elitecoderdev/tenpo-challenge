package com.tenpo.challenge.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    long countByCustomerNameNormalized(String customerNameNormalized);

    List<Transaction> findAllByOrderByTransactionDateDescIdDesc();

    List<Transaction> findAllByCustomerNameNormalizedOrderByTransactionDateDescIdDesc(String customerNameNormalized);
}
