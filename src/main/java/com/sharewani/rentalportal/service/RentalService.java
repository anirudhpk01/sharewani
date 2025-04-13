// 


package com.sharewani.rentalportal.service;
import java.math.BigDecimal;


import com.sharewani.rentalportal.dto.RentalDto;
import com.sharewani.rentalportal.model.enums.RentalStatus;
import com.sharewani.rentalportal.model.*;
import com.sharewani.rentalportal.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.sharewani.rentalportal.service.observer.NotificationSubject;
import com.sharewani.rentalportal.service.observer.TenantNotificationObserver;
import com.sharewani.rentalportal.service.observer.OwnerNotificationObserver;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RentalService {


    @Autowired
private OwnerNotificationObserver ownerObserver;

@Autowired
private TenantNotificationObserver tenantObserver;

// Create and reuse your notification subject
private final NotificationSubject subject = new NotificationSubject();


    private final RentalRepository rentalRepository;
    private final ItemRepository itemRepository;
    private final TenantRepository tenantRepository;

    @Transactional

    

    
public Rental requestRental(RentalDto dto) {
    Item item = itemRepository.findById(dto.getItemId())
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));
    Tenant tenant = tenantRepository.findById(dto.getTenantId())
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

    if (!item.isAvailable()) {
        throw new IllegalStateException("Item not available");
    }

    // Calculate total cost
    long diffMillis = dto.getEndDate().getTime() - dto.getStartDate().getTime();
    long rentalDays = (diffMillis / (1000 * 60 * 60 * 24));
    if (rentalDays == 0) rentalDays = 1;

    BigDecimal totalCost = item.getDailyRate().multiply(BigDecimal.valueOf(rentalDays));

    Rental rental = Rental.builder()
            .item(item)
            .tenant(tenant)
            .startDate(dto.getStartDate())
            .endDate(dto.getEndDate())
            .status(RentalStatus.PENDING)
            .amount(totalCost)
            .build();

    return rentalRepository.save(rental);
}


    public Rental approveRental(Long rentalId) {
        Rental rental = getRentalOrThrow(rentalId);
        rental.setStatus(RentalStatus.APPROVED);
        rental.getItem().setAvailable(false);

        // Set owner and tenant in their respective observers
    ownerObserver.setOwner(rental.getItem().getOwner());
    tenantObserver.setTenant(rental.getTenant());

    // Attach observers to the subject
    subject.attach(ownerObserver);
    subject.attach(tenantObserver);

    // Notify all
    subject.notifyObservers("Rental APPROVED for item: " + rental.getItem().getName());

    subject.detach(ownerObserver);
subject.detach(tenantObserver);



        return rentalRepository.save(rental);
    }

    public Rental rejectRental(Long rentalId) {
        Rental rental = getRentalOrThrow(rentalId);
        rental.setStatus(RentalStatus.REJECTED);

         ownerObserver.setOwner(rental.getItem().getOwner());
    tenantObserver.setTenant(rental.getTenant());

    subject.attach(ownerObserver);
    subject.attach(tenantObserver);

    subject.notifyObservers("Rental REJECTED for item: " + rental.getItem().getName());

    subject.detach(ownerObserver);
subject.detach(tenantObserver);

        return rentalRepository.save(rental);
    }

    public Rental completeRental(Long rentalId) {
        Rental rental = getRentalOrThrow(rentalId);
        rental.setStatus(RentalStatus.COMPLETED);
        rental.getItem().setAvailable(true);

        ownerObserver.setOwner(rental.getItem().getOwner());
    tenantObserver.setTenant(rental.getTenant());

    subject.attach(ownerObserver);
    subject.attach(tenantObserver);

    subject.notifyObservers("Rental COMPLETED for item: " + rental.getItem().getName());


    subject.detach(ownerObserver);
subject.detach(tenantObserver);



        return rentalRepository.save(rental);
    }

    

    public List<Rental> getRentalsByItem(Long itemId) {
    Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("Item not found"));
    return rentalRepository.findByItem(item);
}

public List<Rental> getRentalsByTenant(Long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    return rentalRepository.findByTenant(tenant);
}


    private Rental getRentalOrThrow(Long rentalId) {
        return rentalRepository.findById(rentalId)
                .orElseThrow(() -> new IllegalArgumentException("Rental not found"));
    }
}
