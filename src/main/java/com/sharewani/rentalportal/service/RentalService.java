package com.sharewani.rentalportal.service;

import com.sharewani.rentalportal.dto.RentalDto;
import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.model.enums.RentalStatus;
import com.sharewani.rentalportal.repository.ItemRepository;
import com.sharewani.rentalportal.repository.RentalRepository;
import com.sharewani.rentalportal.repository.TenantRepository;
import com.sharewani.rentalportal.service.factory.RentalFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RentalService {

    private final RentalRepository rentalRepository;
    private final ItemRepository itemRepository;
    private final TenantRepository tenantRepository;
    private final RentalFactory rentalFactory;
    private final NotificationService notificationService;

    public List<Rental> getAllRentals() {
        return rentalRepository.findAll();
    }

    public Optional<Rental> getRentalById(Long id) {
        return rentalRepository.findById(id);
    }

    public List<Rental> getRentalsByTenant(Tenant tenant) {
        return rentalRepository.findByTenant(tenant);
    }

    public List<Rental> getRentalsByItem(Item item) {
        return rentalRepository.findByItem(item);
    }

    @Transactional
    public Rental createRental(RentalDto rentalDto) {
        Item item = itemRepository.findById(rentalDto.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        // Check if item is available using State pattern
        if (!item.handleRentalRequest()) {
            throw new IllegalStateException("Item is not available for rental");
        }

        // Use the factory to create a rental
        Rental rental = rentalFactory.createRental(rentalDto);
        rental = rentalRepository.save(rental);

        // Notify the owner about the rental request
        notificationService.notifyRentalRequest(rental);

        return rental;
    }

    @Transactional
    public Rental approveRental(Long id) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));

        rental.setStatus(RentalStatus.APPROVED);
        
        // Mark the item as unavailable
        Item item = rental.getItem();
        item.setAvailable(false);
        itemRepository.save(item);
        
        rental = rentalRepository.save(rental);
        
        // Notify the tenant about the approval
        notificationService.notifyRentalStatusChange(rental);
        
        return rental;
    }

    @Transactional
    public Rental rejectRental(Long id) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));

        rental.setStatus(RentalStatus.REJECTED);
        rental = rentalRepository.save(rental);
        
        // Notify the tenant about the rejection
        notificationService.notifyRentalStatusChange(rental);
        
        return rental;
    }

    @Transactional
    public Rental completeRental(Long id) {
        Rental rental = rentalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));

        rental.setStatus(RentalStatus.COMPLETED);
        
        // Mark the item as available again
        Item item = rental.getItem();
        item.setAvailable(true);
        itemRepository.save(item);
        
        return rentalRepository.save(rental);
    }
}