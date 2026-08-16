package com.eventplatform.payment.application;

import com.eventplatform.payment.api.RequestContext; import com.eventplatform.payment.domain.*; import com.eventplatform.payment.provider.PaymentProvider; import com.eventplatform.payment.security.AuthenticatedActor; import java.util.*; import org.springframework.stereotype.Service;

@Service
public class RefundApplicationService {
 private final RefundStateService state; private final PaymentProvider provider; private final PaymentRepository payments;
 public RefundApplicationService(RefundStateService state,PaymentProvider provider,PaymentRepository payments){this.state=state;this.provider=provider;this.payments=payments;}
 public Refund refund(UUID paymentId,AuthenticatedActor actor,String key,List<UUID> tickets,String reason,RequestContext context){RefundStateService.ClaimedRefund c=state.claim(paymentId,actor,key,tickets,reason);if(c.existing())return c.refund();PaymentProvider.ProviderResult result=provider.refund(new PaymentProvider.RefundPayment(c.payment().getProviderPaymentId(),key,c.refund().getAmount(),c.refund().getCurrency()));return state.apply(c.refund().getId(),result,context);}
 public Refund compensate(UUID paymentId,String key,RequestContext context){Refund refund=state.claimCompensation(paymentId,key,context);if(refund.getStatus()!=RefundStatus.PROCESSING)return refund;Payment payment=payments.findById(paymentId).orElseThrow();PaymentProvider.ProviderResult result=provider.refund(new PaymentProvider.RefundPayment(payment.getProviderPaymentId(),key,refund.getAmount(),refund.getCurrency()));return state.apply(refund.getId(),result,context);}
}
