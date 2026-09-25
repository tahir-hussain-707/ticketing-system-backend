package com.frequency.ticketing.web.dto;

/** One per-field validation failure inside an {@link ApiError}. */
public record ApiFieldError(String field, String message) {}
