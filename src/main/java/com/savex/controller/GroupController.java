package com.savex.controller;

import com.savex.dto.request.CreateGroupRequest;
import com.savex.dto.response.ApiResponse;
import com.savex.dto.response.GroupResponse;
import com.savex.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new savings group")
    public ApiResponse<GroupResponse> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateGroupRequest request) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ApiResponse.ok("Group created", groupService.createGroup(userId, request));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Get group details")
    public ApiResponse<GroupResponse> get(@PathVariable UUID groupId, @AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok(groupService.getGroup(UUID.fromString(principal.getUsername()), groupId));
    }

    @PostMapping("/join/{inviteCode}")
    @Operation(summary = "Join a group using invite code")
    public ApiResponse<GroupResponse> join(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable String inviteCode) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ApiResponse.ok("Joined group successfully", groupService.joinByInviteCode(userId, inviteCode));
    }

    @GetMapping("/public/open")
    @Operation(summary = "Browse open groups (no auth required)")
    public ApiResponse<Page<GroupResponse>> openGroups(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(groupService.getOpenGroups(pageable));
    }

    @GetMapping("/my")
    @Operation(summary = "Get the current user's groups")
    public ApiResponse<Page<GroupResponse>> myGroups(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ApiResponse.ok(groupService.getMyGroups(userId, pageable));
    }
}
