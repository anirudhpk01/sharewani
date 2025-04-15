package com.sharewani.rentalportal.controller;

import com.sharewani.rentalportal.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DummyViewTest {

    private final ItemRepository itemRepository;

    @GetMapping("/pages/register")
    public String showRegisterForm() {
        return "register";
    }

    @GetMapping("/pages/login")
    public String showLoginForm() {
        return "login";
    }

    @GetMapping("/pages/add-item")
    public String addItemForm() {
        return "add-item";
    }

    @GetMapping("/pages/view-items")
    public String viewAllItems(Model model) {
        model.addAttribute("items", itemRepository.findAll());
        return "items";
    }

    @GetMapping("/pages/rental-form")
public String rentalFormPage() {
    return "rental-form";
}



@GetMapping("/pages/payment")
public String paymentPage() {
    return "payment";
}

@GetMapping("/pages/my-rentals")
public String MyRentals() {
    return "my-rentals";
}

@GetMapping("/pages/shared-rental")
public String sharedRentalPage() {
    return "shared-rental";
}

@GetMapping("/pages/approve-rental")
public String approveRentalPage() {
    return "approve-rental";
}

@GetMapping("/pages/owner-home")
public String ownerHome() {
    return "owner-home";
}

@GetMapping("/pages/tenant-home")
public String tenantHome() {
    return "tenant-home";
}

@GetMapping("/pages/my-items")
public String myItems() {
    return "my-items";
}

@GetMapping("/pages/view-requests")
public String viewRequests() {
    return "view-requests";
}

@GetMapping("/pages/request-rental")
public String requestRentals() {
    return "request-rental";
}

@GetMapping("/pages/view-my-rentals")
public String viewMyRentals() {
    return "view-my-rentals";
}


@GetMapping("/pages/admin-users")
public String adminUsers() {
    return "pages/admin-users";
}

@GetMapping("/pages/admin-items")
public String adminItems() {
    return "pages/admin-items";
}

@GetMapping("/pages/admin-rentals")
public String adminRentals() {
    return "pages/admin-rentals";
}

@GetMapping("/pages/admin-payments")
public String adminPayments() {
    return "pages/admin-payments";
}









}
