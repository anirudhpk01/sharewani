
package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.dto.RentalDto;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.service.RentalService;

import com.sharewani.rentalportal.repository.ItemRepository;    
import com.sharewani.rentalportal.repository.RentalRepository;
import com.sharewani.rentalportal.repository.SharedRentalRepository;
import com.sharewani.rentalportal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.sharewani.rentalportal.model.enums.RentalStatus;

import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.model.Item;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;



@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor

public class TenantDashboardController {

    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final SharedRentalRepository sharedRentalRepository;
    private final TenantRepository tenantRepository;


@GetMapping("/{tenantId}/dashboard-metrics")
public ResponseEntity<Map<String, Integer>> getTenantDashboardStats(@PathVariable Long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

    // ✅ Available Items
    int availableItems = itemRepository.findByAvailableTrue().size();

    // ✅ Active Rentals for this tenant
    List<Rental> allRentals = rentalRepository.findByTenant(tenant);
    int activeRentals = (int) allRentals.stream()
            .filter(r -> r.getStatus() == RentalStatus.APPROVED)
            .count();

    // ✅ Shared Rentals
    int sharedRentals = sharedRentalRepository.findByTenant(tenant).size();

    Map<String, Integer> metrics = new HashMap<>();
    metrics.put("availableItems", availableItems);
    metrics.put("activeRentals", activeRentals);
    metrics.put("sharedRentals", sharedRentals);

    return ResponseEntity.ok(metrics);
}

}