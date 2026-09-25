package com.frequency.ticketing.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(@NotBlank String content) {}
