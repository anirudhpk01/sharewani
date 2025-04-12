package com.sharewani.rentalportal.service.factory;

import com.sharewani.rentalportal.dto.RentalDto;
import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.model.enums.RentalStatus;
import com.sharewani.rentalportal.repository.ItemRepository;
import com.sharewani.rentalportal.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class DefaultRentalFactory implements RentalFactory {

    private final ItemRepository itemRepository;
    private final TenantRepository tenantRepository;

    @Override
    public Rental createRental(RentalDto rentalDto) {
        Item item = itemRepository.findById(rentalDto.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));
        
        Tenant tenant = tenantRepository.findById(rentalDto.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
        
        // Calculate rental amount based on daily rate and rental duration
        long days = ChronoUnit.DAYS.between(
                rentalDto.getStartDate().toInstant(), 
                rentalDto.getEndDate().toInstant()
        );
        
        if (days <= 0) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        
        BigDecimal amount = item.getDailyRate().multiply(BigDecimal.valueOf(days));
        
        // Create and return the rental
        return Rental.builder()
                .item(item)
                .tenant(tenant)
                .startDate(rentalDto.getStartDate())
                .endDate(rentalDto.getEndDate())
                .status(RentalStatus.PENDING)
                .amount(amount)
                .build();
    }
}