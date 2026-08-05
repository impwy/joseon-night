package kr.vamsur.adapter.desktopapi;

import jakarta.validation.constraints.NotNull;
import kr.vamsur.domain.gameplay.UpgradeType;

/**
 * Upgrade selected by the desktop client.
 */
public record GameUpgradeRequest(@NotNull UpgradeType upgradeType) {
}
