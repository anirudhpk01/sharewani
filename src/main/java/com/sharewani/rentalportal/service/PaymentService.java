package com.sharewani.rentalportal.service;

import com.sharewani.rentalportal.dto.PaymentDto;
import com.sharewani.rentalportal.model.Payment;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.enums.PaymentStatus;
import com.sharewani.rentalportal.model.enums.RentalStatus;
import com.sharewani.rentalportal.repository.PaymentRepository;
import com.sharewani.rentalportal.repository.RentalRepository;
import com.sharewani.rentalportal.service.factory.PaymentStrategyFactory;
import com.sharewani.rentalportal.service.strategy.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;


// @Service
// @RequiredArgsConstructor
// public class PaymentService {

//     private final PaymentRepository paymentRepository;
//     private final RentalRepository rentalRepository;
//     private final PaymentStrategyFactory paymentStrategyFactory;
//     private final NotificationService notificationService;

//     public List<Payment> getAllPayments() {
//         return paymentRepository.findAll();
//     }

//     public Optional<Payment> getPaymentById(Long id) {
//         return paymentRepository.findById(id);
//     }

//     public Optional<Payment> getPaymentByRental(Rental rental) {
//         return paymentRepository.findByRental(rental);
//     }

//     @Transactional
//     public Payment processPayment(PaymentDto paymentDto, String paymentType) {
//         Rental rental = rentalRepository.findById(paymentDto.getRentalId())
//                 .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
        
//         // Check if rental is in the right state for payment
//         if (rental.getStatus() != RentalStatus.APPROVED) {
//             throw new IllegalStateException("Payment can only be made for approved rentals");
//         }
        
//         // Use strategy pattern to process payment
//         PaymentStrategy strategy = paymentStrategyFactory.getStrategy(paymentType);
//         Payment payment = strategy.processPayment(paymentDto);
//         payment = paymentRepository.save(payment);
        
//         // If payment is completed, notify the owner
//         if (payment.getStatus() == PaymentStatus.COMPLETED) {
//             notificationService.notifyPaymentReceived(rental);
//         }
        
//         return payment;
//     }

//     @Transactional
//     public Payment updatePaymentStatus(Long id, PaymentStatus status) {
//         Payment payment = paymentRepository.findById(id)
//                 .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        
//         payment.setStatus(status);
        
//         // If payment is now completed, notify the owner
//         if (status == PaymentStatus.COMPLETED) {
//             notificationService.notifyPaymentReceived(payment.getRental());
//         }
        
//         return paymentRepository.save(payment);
//     }
// }

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentStrategyFactory strategyFactory;
    private final RentalRepository rentalRepository;
    private final PaymentRepository paymentRepository;

    public Payment processPayment(PaymentDto dto) {
        Rental rental = rentalRepository.findById(dto.getRentalId())
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));

        if (!rental.getStatus().equals(RentalStatus.APPROVED)) {
            throw new IllegalStateException("Rental must be approved before payment.");
        }

        PaymentStrategy strategy = strategyFactory.getStrategy(dto.getMethod());

        Payment payment = strategy.pay(rental, dto.getAmount());

        return paymentRepository.save(payment);
    }
}
