package com.sharewani.rentalportal.service;

import com.sharewani.rentalportal.dto.ItemDto;
import com.sharewani.rentalportal.model.AvailableState;
import com.sharewani.rentalportal.model.Item;
import com.sharewani.rentalportal.model.Owner;
import com.sharewani.rentalportal.model.UnavailableState;
import com.sharewani.rentalportal.repository.ItemRepository;
import com.sharewani.rentalportal.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemService {

    private final ItemRepository itemRepository;
    private final OwnerRepository ownerRepository;

    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    public List<Item> getAvailableItems() {
        return itemRepository.findByAvailableTrue();
    }

    // public List<Item> getItemsByOwner(Owner owner) {
    //     return itemRepository.findByOwner(owner);
    // }

    public List<Item> getItemsByOwner(Long ownerId) {
    Owner owner = ownerRepository.findById(ownerId)
        .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
    return itemRepository.findByOwner(owner);
}


    public Optional<Item> getItemById(Long id) {
        return itemRepository.findById(id);
    }
   

    @Transactional
    public Item addItem(ItemDto itemDto) {
        Owner owner = ownerRepository.findById(itemDto.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));

        Item item = Item.builder()
                .name(itemDto.getName())
                .description(itemDto.getDescription())
                .dailyRate(itemDto.getDailyRate())
                .owner(owner)
                .available(true)
                .build();

        // Set the appropriate state
        item.setState(new AvailableState());

        // Handle image upload if present
        if (itemDto.getImage() != null && !itemDto.getImage().isEmpty()) {
            String imageUrl = saveImage(itemDto.getImage());
            item.setImageUrl(imageUrl);
        }

        return itemRepository.save(item);
    }

    @Transactional
    public Item updateItem(Long id, ItemDto itemDto) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Item not found"));

        item.setName(itemDto.getName());
        item.setDescription(itemDto.getDescription());
        item.setDailyRate(itemDto.getDailyRate());
        item.setAvailable(itemDto.isAvailable());

        // Update state based on availability
        if (itemDto.isAvailable()) {
            item.setState(new AvailableState());
        } else {
            item.setState(new UnavailableState());
        }

        // Handle image upload if present
        if (itemDto.getImage() != null && !itemDto.getImage().isEmpty()) {
            String imageUrl = saveImage(itemDto.getImage());
            item.setImageUrl(imageUrl);
        }

        return itemRepository.save(item);
    }

    @Transactional
    public void deleteItem(Long id) {
        itemRepository.deleteById(id);
    }

    private String saveImage(MultipartFile file) {
        try {
            // Create directory if it doesn't exist
            String uploadDir = "uploads";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path filePath = uploadPath.resolve(filename);
            
            // Save file
            Files.copy(file.getInputStream(), filePath);
            
            // Return relative path
            return "/" + uploadDir + "/" + filename;
        } catch (IOException e) {
            log.error("Failed to save image", e);
            throw new RuntimeException("Failed to save image", e);
        }
    }
}