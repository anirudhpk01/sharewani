package com.sharewani.rentalportal.repository;

import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.Rental;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.model.enums.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.List;

@Repository
public interface RentalRepository extends JpaRepository<Rental, Long> {
    List<Rental> findByTenant(Tenant tenant);
    List<Rental> findByItem(Item item);
    List<Rental> findByStatus(RentalStatus status);
    List<Rental> findByItemAndStatus(Item item, RentalStatus status);
   
}