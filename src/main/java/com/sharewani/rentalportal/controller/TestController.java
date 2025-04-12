package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.dto.UserDto;
import com.sharewani.rentalportal.model.User;
import com.sharewani.rentalportal.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final UserService userService;

    @PostMapping("/register")
    public User register(@RequestBody UserDto dto) {
        return userService.registerUser(dto);
    }
}
