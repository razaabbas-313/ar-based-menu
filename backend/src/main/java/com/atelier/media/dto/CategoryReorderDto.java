package com.atelier.media.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CategoryReorderDto(
        @NotEmpty List<Long> orderedIds
) {
}
