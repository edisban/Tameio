package com.tameio.identity;

import com.tameio.platform.validation.ValidAfm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterBusinessRequest(
    @NotBlank(message = "ΑΦΜ is required") @ValidAfm String afm,
    @NotBlank(message = "Name is required") @Size(max = 200, message = "Name must be at most 200 characters") String name) {

    /** Accepts "094 014 201" or " 094014201 " as typed, and stores the bare nine digits. */
    public RegisterBusinessRequest {
        afm = afm == null ? null : afm.replaceAll("\\s+", "");
        name = name == null ? null : name.strip();
    }
}
