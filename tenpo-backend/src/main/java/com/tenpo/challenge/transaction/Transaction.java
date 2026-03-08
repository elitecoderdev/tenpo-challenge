package com.tenpo.challenge.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "amount_in_pesos", nullable = false)
    private int amountInPesos;

    @Column(nullable = false, length = 160)
    private String merchant;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_name_normalized", nullable = false, length = 120)
    private String customerNameNormalized;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    public Integer getId() {
        return id;
    }

    public int getAmountInPesos() {
        return amountInPesos;
    }

    public void setAmountInPesos(int amountInPesos) {
        this.amountInPesos = amountInPesos;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerNameNormalized() {
        return customerNameNormalized;
    }

    public void setCustomerNameNormalized(String customerNameNormalized) {
        this.customerNameNormalized = customerNameNormalized;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    @PrePersist
    @PreUpdate
    void normalizeCustomerName() {
        if (customerName != null) {
            customerNameNormalized = canonicalize(customerName);
        }
    }

    public static String canonicalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
