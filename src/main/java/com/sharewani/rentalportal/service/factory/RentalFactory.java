package com.sharewani.rentalportal.service.factory;

import com.sharewani.rentalportal.dto.RentalDto;
import com.sharewani.rentalportal.model.Rental;

public interface RentalFactory {
    Rental createRental(RentalDto rentalDto);
}