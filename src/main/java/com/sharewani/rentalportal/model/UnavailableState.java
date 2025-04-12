package com.sharewani.rentalportal.model;

public class UnavailableState implements ItemAvailabilityState {
    @Override
    public boolean handleRequest(Item item) {
        // Item is unavailable, so request cannot be processed
        return false;
    }
}