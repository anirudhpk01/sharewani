package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.dto.RentalDto;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.service.RentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController
// @RequestMapping("/rentals")
// @RequiredArgsConstructor
// public class RentalController {

//     private final RentalService rentalService;

//     @PostMapping
//     public ResponseEntity<Rental> requestRental(@RequestBody RentalDto rentalDto) {
//         return ResponseEntity.ok(rentalService.requestRental(rentalDto));
//     }

//     @PutMapping("/{id}/approve")
//     public ResponseEntity<Rental> approveRental(@PathVariable Long id) {
//         return ResponseEntity.ok(rentalService.approveRental(id));
//     }

//     @PutMapping("/{id}/reject")
//     public ResponseEntity<Rental> rejectRental(@PathVariable Long id) {
//         return ResponseEntity.ok(rentalService.rejectRental(id));
//     }

//     @PutMapping("/{id}/complete")
//     public ResponseEntity<Rental> completeRental(@PathVariable Long id) {
//         return ResponseEntity.ok(rentalService.completeRental(id));
//     }

//     @GetMapping("/item/{itemId}")
//     public ResponseEntity<List<Rental>> getRentalsByItem(@PathVariable Long itemId) {
//         return ResponseEntity.ok(rentalService.getRentalsByItem(itemId));
//     }

//     @GetMapping("/tenant/{tenantId}")
//     public ResponseEntity<List<Rental>> getRentalsByTenant(@PathVariable Long tenantId) {
//         return ResponseEntity.ok(rentalService.getRentalsByTenant(tenantId));
//     }
// }




@RestController
@RequestMapping("/rentals")
@RequiredArgsConstructor
public class RentalController {

    private final RentalService rentalService;

    @PostMapping
    public ResponseEntity<Rental> requestRental(@RequestBody RentalDto rentalDto) {
        return ResponseEntity.ok(rentalService.requestRental(rentalDto));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<Rental> approveRental(@PathVariable Long id) {
        return ResponseEntity.ok(rentalService.approveRental(id));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Rental> rejectRental(@PathVariable Long id) {
        return ResponseEntity.ok(rentalService.rejectRental(id));
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Rental> completeRental(@PathVariable Long id) {
        return ResponseEntity.ok(rentalService.completeRental(id));
    }

    @GetMapping("/item/{itemId}")
    public ResponseEntity<List<Rental>> getRentalsByItem(@PathVariable Long itemId) {
        return ResponseEntity.ok(rentalService.getRentalsByItem(itemId));
    }

    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<Rental>> getRentalsByTenant(@PathVariable Long tenantId) {
        return ResponseEntity.ok(rentalService.getRentalsByTenant(tenantId));
    }
}
