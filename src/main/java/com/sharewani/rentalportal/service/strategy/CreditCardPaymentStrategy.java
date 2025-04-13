package com.sharewani.rentalportal.service.strategy;

import com.sharewani.rentalportal.dto.PaymentDto;
import com.sharewani.rentalportal.model.Payment;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.enums.PaymentStatus;
import com.sharewani.rentalportal.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.math.BigDecimal;
import java.time.LocalDate;
// @Component
// @RequiredArgsConstructor
// public class CreditCardPaymentStrategy implements PaymentStrategy {

//     private final RentalRepository rentalRepository;

//     @Override
//     public Payment processPayment(PaymentDto paymentDto) {
//         // In a real app, we would call a payment gateway here
//         Rental rental = rentalRepository.findById(paymentDto.getRentalId())
//                 .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
        
//         // Simulate payment processing
//         return Payment.builder()
//                 .amount(paymentDto.getAmount())
//                 .date(new Date())
//                 .status(PaymentStatus.COMPLETED)
//                 .rental(rental)
//                 .build();
//     }

//     @Override
//     public String getType() {
//         return "CREDIT_CARD";
//     }
// }

@Component("CARD")
public class CreditCardPaymentStrategy implements PaymentStrategy {
    @Override
    public Payment pay(Rental rental, BigDecimal amount) {
        return Payment.builder()
                .rental(rental)
                .amount(amount)
                .method("CARD")
                .paymentDate(LocalDate.now())
                .build();
    }
}
