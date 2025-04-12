package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.dto.UserDto;
import com.sharewani.rentalportal.model.User;
import com.sharewani.rentalportal.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> registerUser(@RequestBody UserDto userDto) {
        User user = userService.registerUser(userDto);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDto userDto) {
        return userService.getUserByEmail(userDto.getEmail())
                .map(user -> {
                    if (user.getPassword().equals(userDto.getPassword())) {
                        return ResponseEntity.ok(user);
                    } else {
                        return ResponseEntity.badRequest().body("Invalid password");
                    }
                })
                .orElse(ResponseEntity.badRequest().body("User not found"));
    }
}
