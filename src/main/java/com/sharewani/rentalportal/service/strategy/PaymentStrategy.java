package com.sharewani.rentalportal.service.strategy;

import com.sharewani.rentalportal.dto.PaymentDto;
import com.sharewani.rentalportal.model.Payment;
import com.sharewani.rentalportal.model.Rental;
import java.math.BigDecimal;

// public interface PaymentStrategy {
//     Payment processPayment(PaymentDto paymentDto);
//     String getType();
// }

public interface PaymentStrategy {
    Payment pay(Rental rental, BigDecimal amount);
}
