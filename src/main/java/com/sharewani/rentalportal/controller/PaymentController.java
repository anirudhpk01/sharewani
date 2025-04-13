package com.sharewani.rentalportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.sharewani.rentalportal.service.PaymentService;
import com.sharewani.rentalportal.dto.PaymentDto;
import com.sharewani.rentalportal.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> processPayment(@RequestBody PaymentDto dto) {
        return ResponseEntity.ok(paymentService.processPayment(dto));
    }
}
