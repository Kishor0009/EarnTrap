package com.earntrap.backend.dto;

import com.earntrap.backend.enums.UserStatus;

public class UserProfileResponse {

    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String phone;
    private UserStatus status;

    public UserProfileResponse() {
    }

    public UserProfileResponse(
            Long id,
            String fullName,
            String username,
            String email,
            String phone,
            UserStatus status
    ) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }
}