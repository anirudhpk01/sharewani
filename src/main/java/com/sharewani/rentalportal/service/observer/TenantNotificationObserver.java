package com.sharewani.rentalportal.service.observer;

import com.sharewani.rentalportal.model.Tenant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantNotificationObserver implements Observer {

    private Tenant tenant;

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    @Override
    public void update(String message) {
        if (tenant != null) {
            // In a real application, we would send an email or push notification here
            log.info("Notification for tenant {}: {}", tenant.getName(), message);
        }
    }
}