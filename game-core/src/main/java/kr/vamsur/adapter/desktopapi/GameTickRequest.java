package kr.vamsur.adapter.desktopapi;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Fixed-step duration sent by the desktop client, in seconds.
 */
public record GameTickRequest(
        @Positive double deltaSeconds,
        @Min(1) @Max(15) int steps
) {
}
