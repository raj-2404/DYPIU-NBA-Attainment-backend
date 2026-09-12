package com.dypiu.nba.controller;

import com.dypiu.nba.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.<Map<String, String>>builder()
                .success(true)
                .message("DYPIU NBA Attainment Backend is operational")
                .data(Map.of(
                        "status", "UP",
                        "service", "DYPIU NBA Attainment System"
                ))
                .build());
    }
}
