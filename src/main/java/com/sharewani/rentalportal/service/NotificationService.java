package com.sharewani.rentalportal.service;

import com.sharewani.rentalportal.model.Owner;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.service.observer.NotificationSubject;
import com.sharewani.rentalportal.service.observer.OwnerNotificationObserver;
import com.sharewani.rentalportal.service.observer.TenantNotificationObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final OwnerNotificationObserver ownerObserver;
    private final TenantNotificationObserver tenantObserver;
    
    public void notifyRentalRequest(Rental rental) {
        Owner owner = rental.getItem().getOwner();
        NotificationSubject subject = new NotificationSubject();
        
        ownerObserver.setOwner(owner);
        subject.attach(ownerObserver);
        
        String message = String.format(
            "New rental request for item '%s' from %s to %s",
            rental.getItem().getName(),
            rental.getStartDate(),
            rental.getEndDate()
        );
        
        subject.notifyObservers(message);
    }
    
    public void notifyRentalStatusChange(Rental rental) {
        Tenant tenant = rental.getTenant();
        NotificationSubject subject = new NotificationSubject();
        
        tenantObserver.setTenant(tenant);
        subject.attach(tenantObserver);
        
        String message = String.format(
            "Your rental request for item '%s' has been %s",
            rental.getItem().getName(),
            rental.getStatus().toString().toLowerCase()
        );
        
        subject.notifyObservers(message);
    }
    
    public void notifyPaymentReceived(Rental rental) {
        Owner owner = rental.getItem().getOwner();
        NotificationSubject subject = new NotificationSubject();
        
        ownerObserver.setOwner(owner);
        subject.attach(ownerObserver);
        
        String message = String.format(
            "Payment received for rental of item '%s'",
            rental.getItem().getName()
        );
        
        subject.notifyObservers(message);
    }
}