package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.model.*;
import com.sharewani.rentalportal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OwnerRepository ownerRepo;
    private final TenantRepository tenantRepo;
    private final ItemRepository itemRepo;
    private final RentalRepository rentalRepo;
    private final PaymentRepository paymentRepo;

    @GetMapping("/owners")
    public List<Owner> getAllOwners() {
        return ownerRepo.findAll();
    }

    @GetMapping("/tenants")
    public List<Tenant> getAllTenants() {
        return tenantRepo.findAll();
    }

    @GetMapping("/items")
    public List<Item> getAllItems() {
        return itemRepo.findAll();
    }

    @GetMapping("/rentals")
    public List<Rental> getAllRentals() {
        return rentalRepo.findAll();
    }

    @GetMapping("/payments")
    public List<Payment> getAllPayments() {
        return paymentRepo.findAll();
    }
}
