package com.sharewani.rentalportal.repository;

import com.sharewani.rentalportal.model.Payment;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRental(Rental rental);
    List<Payment> findByStatus(PaymentStatus status);
}