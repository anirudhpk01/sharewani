package com.sharewani.rentalportal.service.strategy;

import com.sharewani.rentalportal.dto.PaymentDto;
import com.sharewani.rentalportal.model.Payment;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.enums.PaymentStatus;
import com.sharewani.rentalportal.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class BankTransferPaymentStrategy implements PaymentStrategy {

    private final RentalRepository rentalRepository;

    @Override
    public Payment processPayment(PaymentDto paymentDto) {
        // In a real app, we would verify bank transfer details here
        Rental rental = rentalRepository.findById(paymentDto.getRentalId())
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
        
        // Bank transfers typically take time to process
        return Payment.builder()
                .amount(paymentDto.getAmount())
                .date(new Date())
                .status(PaymentStatus.PENDING) // Bank transfers start as pending
                .rental(rental)
                .build();
    }

    @Override
    public String getType() {
        return "BANK_TRANSFER";
    }
}