package com.eventplatform.payment.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,UUID>{List<PaymentTransaction> findAllByPaymentIdOrderByOccurredAt(UUID paymentId);}
