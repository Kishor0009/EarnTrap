package com.earntrap.backend.service;

import com.earntrap.backend.dto.AuthResponse;
import com.earntrap.backend.dto.LoginRequest;
import com.earntrap.backend.dto.RegisterRequest;
import com.earntrap.backend.entity.User;
import com.earntrap.backend.entity.UserProgress;
import com.earntrap.backend.repository.UserProgressRepository;
import com.earntrap.backend.repository.UserRepository;
import com.earntrap.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserProgressRepository userProgressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserProgressRepository userProgressRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userProgressRepository = userProgressRepository;
    }

    @Override
    @Transactional
    public String register(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .build();

        User savedUser = userRepository.save(user);

        // Create initial progress for the newly registered user.
        UserProgress progress = new UserProgress(savedUser);
        userProgressRepository.save(progress);

        return "Registration successful";
    }


    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        String usernameOrEmail = request.getUsernameOrEmail();

        User user = userRepository.findByUsername(usernameOrEmail)
                .orElse(null);

        // If the username was not found, search by email.
        if (user == null) {
            user = userRepository.findByEmail(usernameOrEmail)
                    .orElse(null);
        }

        // Reject invalid credentials.
        if (user == null) {
            throw new RuntimeException(
                    "Invalid username/email or password"
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new RuntimeException(
                    "Invalid username/email or password"
            );
        }

        if (user.getStatus() == null
                || user.getStatus() == com.earntrap.backend.enums.UserStatus.BLOCKED
                || user.getStatus() == com.earntrap.backend.enums.UserStatus.INACTIVE) {
            throw new RuntimeException("User account is not active");
        }

        String token = jwtService.generateToken(user.getUsername());

        return new AuthResponse(token, "Login successful");
    }

}