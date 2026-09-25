package com.frequency.ticketing.web.dto;

import com.frequency.ticketing.domain.user.UserRole;
import java.util.UUID;

public record LoginResponse(UUID id, String name, String email, UserRole role) {}
