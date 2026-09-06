package com.multichat.common.web;

import com.multichat.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {
    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
