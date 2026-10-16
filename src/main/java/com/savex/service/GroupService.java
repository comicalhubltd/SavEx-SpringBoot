package com.savex.service;

import com.savex.dto.request.CreateGroupRequest;
import com.savex.dto.response.GroupResponse;
import com.savex.entity.Group;
import com.savex.entity.GroupMember;
import com.savex.entity.User;
import com.savex.entity.enums.GroupStatus;
import com.savex.exception.BadRequestException;
import com.savex.exception.NotFoundException;
import com.savex.repository.GroupRepository;
import com.savex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupResponse createGroup(UUID creatorId, CreateGroupRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Group group = Group.builder()
                .creator(creator)
                .name(request.name())
                .description(request.description())
                .contributionKobo(request.contributionNaira() * 100)
                .maxMembers((short) request.maxMembers())
                .cycleFrequency(request.cycleFrequency())
                .payoutOrder(request.payoutOrder())
                .startDate(request.startDate())
                .totalCycles((short) request.maxMembers())
                .rules(request.rules())
                .inviteCode(generateInviteCode())
                .build();

        GroupMember creatorMember = GroupMember.builder()
                .group(group)
                .user(creator)
                .build();
        group.getMembers().add(creatorMember);

        group = groupRepository.save(group);
        log.info("Group created: {} by user {}", group.getId(), creatorId);

        return GroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID userId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        if (group.getDeletedAt() != null || group.getMembers().stream().noneMatch(member -> member.isActive() && member.getUser().getId().equals(userId))) {
            throw new NotFoundException("Group not found");
        }
        return GroupResponse.from(group);
    }

    @Transactional
    public GroupResponse joinByInviteCode(UUID userId, String inviteCode) {
        Group group = groupRepository.findByInviteCodeAndDeletedAtIsNull(inviteCode.toUpperCase())
                .orElseThrow(() -> new NotFoundException("Invalid invite code"));

        if (group.getStatus() != GroupStatus.OPEN) {
            throw new BadRequestException("Group is not accepting new members");
        }
        if (group.isFull()) {
            throw new BadRequestException("Group is full");
        }

        boolean alreadyMember = group.getMembers().stream()
                .anyMatch(m -> m.getUser().getId().equals(userId) && m.isActive());
        if (alreadyMember) {
            throw new BadRequestException("You are already a member of this group");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        GroupMember newMember = GroupMember.builder()
                .group(group)
                .user(user)
                .build();
        group.getMembers().add(newMember);

        if (group.isFull()) {
            group.setStatus(GroupStatus.ACTIVE);
        }

        group = groupRepository.save(group);
        log.info("User {} joined group {}", userId, group.getId());

        return GroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public Page<GroupResponse> getOpenGroups(Pageable pageable) {
        return groupRepository.findByStatusAndDeletedAtIsNull(GroupStatus.OPEN, pageable)
                .map(g -> new GroupResponse(g.getId(), g.getName(), g.getDescription(), g.getContributionNaira(),
                    g.getMaxMembers(), g.getMemberCount(), g.getCycleFrequency(), g.getPayoutOrder(),
                    g.getStartDate(), g.getStatus(), null, g.getTotalCycles(), g.getCurrentCycle(), null));
    }

    @Transactional(readOnly = true)
    public Page<GroupResponse> getMyGroups(UUID userId, Pageable pageable) {
        return groupRepository.findGroupsByMemberId(userId, pageable)
                .map(GroupResponse::from);
    }

    private String generateInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (groupRepository.findByInviteCodeAndDeletedAtIsNull(code).isPresent());
        return code;
    }
}
