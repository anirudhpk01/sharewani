package com.sharewani.rentalportal.service;

import com.sharewani.rentalportal.dto.UserDto;
import com.sharewani.rentalportal.model.Owner;
import com.sharewani.rentalportal.model.Tenant;
import com.sharewani.rentalportal.model.User;
import com.sharewani.rentalportal.repository.OwnerRepository;
import com.sharewani.rentalportal.repository.TenantRepository;
import com.sharewani.rentalportal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OwnerRepository ownerRepository;
    private final TenantRepository tenantRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User registerUser(UserDto userDto) {
        if (userRepository.existsByEmail(userDto.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        if ("OWNER".equals(userDto.getUserType())) {
            Owner owner = Owner.builder()
                    .name(userDto.getName())
                    .email(userDto.getEmail())
                    .password(userDto.getPassword()) // In a real app, we would encrypt this
                    .phone(userDto.getPhone())
                    .build();
            return ownerRepository.save(owner);
        } else if ("TENANT".equals(userDto.getUserType())) {
            Tenant tenant = Tenant.builder()
                    .name(userDto.getName())
                    .email(userDto.getEmail())
                    .password(userDto.getPassword()) // In a real app, we would encrypt this
                    .phone(userDto.getPhone())
                    .build();
            return tenantRepository.save(tenant);
        } else {
            throw new IllegalArgumentException("Invalid user type");
        }
    }
}