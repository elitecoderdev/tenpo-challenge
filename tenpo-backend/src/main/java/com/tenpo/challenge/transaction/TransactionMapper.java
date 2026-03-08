package com.tenpo.challenge.transaction;

import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmountInPesos(),
                transaction.getMerchant(),
                transaction.getCustomerName(),
                transaction.getTransactionDate()
        );
    }
}
