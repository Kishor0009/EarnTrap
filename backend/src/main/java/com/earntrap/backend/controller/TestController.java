package com.earntrap.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/api/test/protected")
    public Map<String, String> protectedEndpoint() {

        Map<String, String> response = new HashMap<>();

        response.put("message", "JWT authentication successful!");
        response.put("status", "authenticated");

        return response;
    }
}