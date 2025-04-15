package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.dto.ItemDto;
import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.Owner;
import com.sharewani.rentalportal.service.ItemService;
import com.sharewani.rentalportal.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        return ResponseEntity.ok(itemService.getAllItems());
    }

    @GetMapping("/available")
    public ResponseEntity<List<Item>> getAvailableItems() {
        return ResponseEntity.ok(itemService.getAvailableItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Long id) {
        return itemService.getItemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // @GetMapping("/owner/{ownerId}")
    // public ResponseEntity<List<Item>> getItemsByOwner(@PathVariable Long ownerId) {
    //     Owner owner = (Owner) userService.getUserById(ownerId)
    //             .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
    //     return ResponseEntity.ok(itemService.getItemsByOwner(owner));
    // }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<Item> addItem(@Valid @ModelAttribute ItemDto itemDto) {
        return ResponseEntity.ok(itemService.addItem(itemDto));
    }

    @PutMapping(value = "/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<Item> updateItem(@PathVariable Long id, @Valid @ModelAttribute ItemDto itemDto) {
        return ResponseEntity.ok(itemService.updateItem(id, itemDto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/owner/{ownerId}")
public ResponseEntity<List<Item>> getItemsByOwner(@PathVariable Long ownerId) {
    return ResponseEntity.ok(itemService.getItemsByOwner(ownerId));
}

}
