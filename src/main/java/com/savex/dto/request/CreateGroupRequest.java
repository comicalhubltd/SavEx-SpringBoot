package com.savex.dto.request;

import com.savex.entity.enums.CycleFrequency;
import com.savex.entity.enums.PayoutOrder;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreateGroupRequest(
        @NotBlank @Size(min = 3, max = 100) String name,
        @Size(max = 500) String description,
        @Min(500) @Max(100000000) long contributionNaira,
        @Min(2) @Max(50) int maxMembers,
        @NotNull CycleFrequency cycleFrequency,
        @NotNull PayoutOrder payoutOrder,
        @NotNull @Future LocalDate startDate,
        String rules
) {}
