package com.sharewani.rentalportal.service.strategy;

import com.sharewani.rentalportal.dto.PaymentDto;
import com.sharewani.rentalportal.model.Payment;

public interface PaymentStrategy {
    Payment processPayment(PaymentDto paymentDto);
    String getType();
}