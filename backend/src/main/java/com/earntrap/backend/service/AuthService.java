package com.earntrap.backend.service;

import com.earntrap.backend.dto.AuthResponse;
import com.earntrap.backend.dto.LoginRequest;
import com.earntrap.backend.dto.RegisterRequest;

public interface AuthService {

    String register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}