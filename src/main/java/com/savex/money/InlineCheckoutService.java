package com.savex.money;

import com.savex.repository.UserRepository;
import com.savex.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class InlineCheckoutService {
    private final MoneySettings settings;
    private final MoneyOrderRepository orders;
    private final UserRepository users;
    private final com.savex.verification.AccountAssessmentService assessments;

    public record Customer(String name,String email,String phone_number) {}
    public record Checkout(String public_key,String tx_ref,BigDecimal amount,String currency,Customer customer) {}

    private MoneyOrder owned(UUID customerId,UUID orderId) {
        MoneyOrder order=orders.lock(orderId).orElseThrow(()->new NotFoundException("Payment not found"));
        if(!order.getUserId().equals(customerId)) throw new NotFoundException("Payment not found");
        if(order.getKind()!=MoneyOrder.Kind.DEPOSIT || order.getDebitSubaccount()!=null || !settings.getMode().name().equals(order.getProviderMode()))
            throw new BadRequestException("This payment cannot use wallet funding checkout");
        return order;
    }
    @Transactional public Checkout prepare(UUID customerId,UUID orderId) {
        settings.requireInline();
        MoneyOrder order=owned(customerId,orderId);
        if(!assessments.assess(customerId).fundingAvailable()) throw new BadRequestException("This account is not currently eligible for wallet funding");
        if(!Set.of(MoneyOrder.Status.QUEUED,MoneyOrder.Status.PENDING).contains(order.getStatus()))
            throw new BadRequestException("Payment is processing or closed. Refresh its status before continuing");
        // Inline starts the provider payment; the worker only verifies this saved order.
        order.setStatus(MoneyOrder.Status.PENDING);
        order.setNextCheckAt(Instant.now().plusSeconds(60));
        var user=users.findById(customerId).orElseThrow();
        return new Checkout(settings.getFlutterwavePublicKey(),order.getReference(),
            BigDecimal.valueOf(order.providerAmount(),2),order.getCurrency(),
            new Customer(user.getFullName(),user.getEmail(),user.getPhone()));
    }
    @Transactional public void check(UUID customerId,UUID orderId) {
        settings.requireEnabled();
        MoneyOrder order=owned(customerId,orderId);
        if(order.getStatus()==MoneyOrder.Status.PENDING) order.setNextCheckAt(Instant.now());
        // Browser completion is a wake-up only; never proof of payment or a wallet credit.
    }
}
