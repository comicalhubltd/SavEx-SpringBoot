package com.savex.dto.response;

import com.savex.entity.Group;
import com.savex.entity.enums.CycleFrequency;
import com.savex.entity.enums.GroupStatus;
import com.savex.entity.enums.PayoutOrder;

import java.time.LocalDate;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String description,
        double contributionNaira,
        int maxMembers,
        int currentMembers,
        CycleFrequency cycleFrequency,
        PayoutOrder payoutOrder,
        LocalDate startDate,
        GroupStatus status,
        String inviteCode,
        int totalCycles,
        int currentCycle,
        String creatorName
) {
    public static GroupResponse from(Group g) {
        return new GroupResponse(
                g.getId(), g.getName(), g.getDescription(),
                g.getContributionNaira(), g.getMaxMembers(), g.getMemberCount(),
                g.getCycleFrequency(), g.getPayoutOrder(), g.getStartDate(),
                g.getStatus(), g.getInviteCode(),
                g.getTotalCycles(), g.getCurrentCycle(),
                g.getCreator().getFullName()
        );
    }
}
