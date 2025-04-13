package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.dto.SharedRentalDto;
import com.sharewani.rentalportal.model.SharedRental;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.repository.TenantRepository;
import com.sharewani.rentalportal.service.SharedRentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shared-rentals")
@RequiredArgsConstructor
public class SharedRentalController {

    private final SharedRentalService sharedRentalService;
    private final TenantRepository tenantRepository;

    @GetMapping
    public ResponseEntity<List<SharedRental>> getAllSharedRentals() {
        return ResponseEntity.ok(sharedRentalService.getAllSharedRentals());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SharedRental> getSharedRentalById(@PathVariable Long id) {
        return sharedRentalService.getSharedRentalById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SharedRental> createSharedRental(@RequestBody SharedRentalDto dto) {
        return ResponseEntity.ok(sharedRentalService.createSharedRental(dto));
    }

    @PostMapping("/{sharedRentalId}/join/{tenantId}")
    public ResponseEntity<SharedRental> joinSharedRental(
            @PathVariable Long sharedRentalId,
            @PathVariable Long tenantId) {
        return ResponseEntity.ok(sharedRentalService.addTenantToSharedRental(sharedRentalId, tenantId));
    }

    @DeleteMapping("/{sharedRentalId}/remove/{tenantId}")
    public ResponseEntity<SharedRental> removeTenantFromSharedRental(
            @PathVariable Long sharedRentalId,
            @PathVariable Long tenantId) {
        return ResponseEntity.ok(sharedRentalService.removeTenantFromSharedRental(sharedRentalId, tenantId));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<SharedRental>> getSharedRentalsByTenant(@PathVariable Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        return ResponseEntity.ok(sharedRentalService.getSharedRentalsByTenant(tenant));
    }
}
