package com.sharewani.rentalportal.model;

public class AvailableState implements ItemAvailabilityState {
    @Override
    public boolean handleRequest(Item item) {
        // Item is available, so request can be processed
        return true;
    }
}