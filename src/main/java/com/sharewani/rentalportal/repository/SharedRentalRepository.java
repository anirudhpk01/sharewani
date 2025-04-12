package com.sharewani.rentalportal.repository;

import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.SharedRental;
import com.sharewani.rentalportal.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedRentalRepository extends JpaRepository<SharedRental, Long> {
    List<SharedRental> findByItem(Item item);
    
    @Query("SELECT sr FROM SharedRental sr JOIN sr.tenants t WHERE t = :tenant")
    List<SharedRental> findByTenant(Tenant tenant);
}