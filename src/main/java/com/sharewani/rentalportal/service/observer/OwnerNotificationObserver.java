package com.sharewani.rentalportal.service.observer;

import com.sharewani.rentalportal.model.Owner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OwnerNotificationObserver implements Observer {

    private Owner owner;

    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @Override
    public void update(String message) {
        if (owner != null) {
            // In a real application, we would send an email or push notification here
            log.info("Notification for owner {}: {}", owner.getName(), message);
        }
    }
}