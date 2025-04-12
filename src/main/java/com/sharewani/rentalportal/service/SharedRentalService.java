package com.sharewani.rentalportal.service;

import com.sharewani.rentalportal.dto.SharedRentalDto;
import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.SharedRental;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.repository.ItemRepository;
import com.sharewani.rentalportal.repository.SharedRentalRepository;
import com.sharewani.rentalportal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SharedRentalService {

    private final SharedRentalRepository sharedRentalRepository;
    private final ItemRepository itemRepository;
    private final TenantRepository tenantRepository;

    public List<SharedRental> getAllSharedRentals() {
        return sharedRentalRepository.findAll();
    }

    public Optional<SharedRental> getSharedRentalById(Long id) {
        return sharedRentalRepository.findById(id);
    }

    public List<SharedRental> getSharedRentalsByTenant(Tenant tenant) {
        return sharedRentalRepository.findByTenant(tenant);
    }

    @Transactional
    public SharedRental createSharedRental(SharedRentalDto sharedRentalDto) {
        Item item = itemRepository.findById(sharedRentalDto.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        List<Tenant> tenants = new ArrayList<>();
        if (sharedRentalDto.getTenantIds() != null && !sharedRentalDto.getTenantIds().isEmpty()) {
            for (Long tenantId : sharedRentalDto.getTenantIds()) {
                Tenant tenant = tenantRepository.findById(tenantId)
                        .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
                tenants.add(tenant);
            }
        }

        SharedRental sharedRental = SharedRental.builder()
                .item(item)
                .tenants(tenants)
                .maxShareCount(sharedRentalDto.getMaxShareCount())
                .schedule(sharedRentalDto.getSchedule())
                .build();

        return sharedRentalRepository.save(sharedRental);
    }

    @Transactional
    public SharedRental addTenantToSharedRental(Long sharedRentalId, Long tenantId) {
        SharedRental sharedRental = sharedRentalRepository.findById(sharedRentalId)
                .orElseThrow(() -> new IllegalArgumentException("Shared rental not found"));
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        
        if (sharedRental.getTenants().size() >= sharedRental.getMaxShareCount()) {
            throw new IllegalStateException("Maximum number of tenants reached for this shared rental");
        }
        
        sharedRental.getTenants().add(tenant);
        return sharedRentalRepository.save(sharedRental);
    }

    @Transactional
    public SharedRental removeTenantFromSharedRental(Long sharedRentalId, Long tenantId) {
        SharedRental sharedRental = sharedRentalRepository.findById(sharedRentalId)
                .orElseThrow(() -> new IllegalArgumentException("Shared rental not found"));
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        
        sharedRental.getTenants().remove(tenant);
        return sharedRentalRepository.save(sharedRental);
    }
}