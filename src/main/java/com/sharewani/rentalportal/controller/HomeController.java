package com.sharewani.rentalportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("title", "Sharewani - A Shared Rental Portal");
        model.addAttribute("message", "Welcome to Sharewani, where sharing is caring!");
        return "home";
    }
}