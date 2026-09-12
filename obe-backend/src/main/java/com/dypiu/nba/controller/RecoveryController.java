package com.dypiu.nba.controller;

import com.dypiu.nba.dto.ApiResponse;
import com.dypiu.nba.dto.DeletedItemDto;
import com.dypiu.nba.dto.RecoverySummaryDto;
import com.dypiu.nba.dto.RestoreItemRequestDto;
import com.dypiu.nba.security.CurrentUserScope;
import com.dypiu.nba.security.CurrentUserScopeService;
import com.dypiu.nba.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/recovery")
@RequiredArgsConstructor
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final CurrentUserScopeService currentUserScopeService;

    private void enforceIqac(Principal principal) {
        CurrentUserScope scope = currentUserScopeService.getCurrentUserScope(principal);
        if (scope == null || !scope.isIqac()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: Deleted item recovery can only be accessed by IQAC.");
        }
    }

    @GetMapping("/deleted-items")
    public ResponseEntity<ApiResponse<List<DeletedItemDto>>> getDeletedItems(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            Principal principal) {

        enforceIqac(principal);

        List<DeletedItemDto> items = recoveryService.getDeletedItems(resourceType, search, from, to);

        return ResponseEntity.ok(ApiResponse.<List<DeletedItemDto>>builder()
                .success(true)
                .message("Retrieved " + items.size() + " deleted item(s)")
                .data(items)
                .build());
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<RecoverySummaryDto>> getSummary(Principal principal) {
        enforceIqac(principal);

        RecoverySummaryDto summary = recoveryService.getSummary();

        return ResponseEntity.ok(ApiResponse.<RecoverySummaryDto>builder()
                .success(true)
                .data(summary)
                .build());
    }

    @PostMapping("/{resourceType}/{id}/restore")
    public ResponseEntity<ApiResponse<DeletedItemDto>> restoreItem(
            @PathVariable String resourceType,
            @PathVariable String id,
            @RequestBody(required = false) RestoreItemRequestDto request,
            Principal principal) {

        enforceIqac(principal);

        String reason = (request != null) ? request.getReason() : null;
        DeletedItemDto restored = recoveryService.restoreItem(resourceType, id, reason);

        return ResponseEntity.ok(ApiResponse.<DeletedItemDto>builder()
                .success(true)
                .message("Successfully restored " + restored.getResourceType() + " '" + restored.getName() + "'")
                .data(restored)
                .build());
    }
}
