package com.frequency.ticketing.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReassignRequest(@NotNull UUID assigneeId) {}
