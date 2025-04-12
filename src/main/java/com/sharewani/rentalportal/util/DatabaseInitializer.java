package com.sharewani.rentalportal.util;

import com.sharewani.rentalportal.model.Owner;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.repository.OwnerRepository;
import com.sharewani.rentalportal.repository.TenantRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer {

    private final OwnerRepository ownerRepository;
    private final TenantRepository tenantRepository;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing database with sample data...");
        
        // Create a sample owner if none exists
        if (ownerRepository.count() == 0) {
            Owner owner = Owner.builder()
                .name("Sample Owner")
                .email("owner@example.com")
                .password("password")
                .phone("1234567890")
                .build();
            
            ownerRepository.save(owner);
            log.info("Sample owner created");
        }
        
        // Create a sample tenant if none exists
        if (tenantRepository.count() == 0) {
            Tenant tenant = Tenant.builder()
                .name("Sample Tenant")
                .email("tenant@example.com")
                .password("password")
                .phone("9876543210")
                .build();
            
            tenantRepository.save(tenant);
            log.info("Sample tenant created");
        }
        
        log.info("Database initialization completed");
    }
}