package com.fintech.engine.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(
    @Size(max = 100, message = "First name must be at most 100 characters")
    String firstName,

    @Size(max = 100, message = "Last name must be at most 100 characters")
    String lastName,

    @Size(max = 32, message = "Phone number must be at most 32 characters")
    String phoneNumber,

    @Size(max = 255, message = "Address line must be at most 255 characters")
    String addressLine,

    @Size(max = 100, message = "City must be at most 100 characters")
    String city,

    @Size(max = 64, message = "Country must be at most 64 characters")
    String country,

    @Size(max = 20, message = "Postal code must be at most 20 characters")
    String postalCode,

    LocalDate dateOfBirth
) {}

