package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.annotation.PostConstruct;

@Controller
@RequiredArgsConstructor
public class ViewController {

    @PostConstruct
public void init() {
    System.out.println("🟢 ViewController loaded!");
}

    private final ItemRepository itemRepository;

    

    @GetMapping("/sharewani/register")
    public String showRegisterForm() {
        System.out.println("🔥 Register view handler called!");
        return "register";
    }

    @GetMapping("/sharewani/login")
    public String showLoginForm() {
        return "login";
    }

    @GetMapping("/sharewani/add-item")
    public String addItemForm() {
        return "add-item";
    }

    @GetMapping("/sharewani/view-items")
    public String viewAllItems(Model model) {
        model.addAttribute("items", itemRepository.findAll());
        return "items";
    }

    
}
