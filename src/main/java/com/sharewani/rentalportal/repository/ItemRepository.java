package com.sharewani.rentalportal.repository;

import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.Owner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByOwner(Owner owner);
    List<Item> findByAvailableTrue();
}