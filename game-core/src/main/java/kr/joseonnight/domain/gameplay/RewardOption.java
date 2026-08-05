package kr.joseonnight.domain.gameplay;

import java.util.Objects;

/**
 * One server-issued choice. Only an ID from the current choice list can be applied.
 */
public record RewardOption(
        String optionId,
        RewardKind kind,
        String targetId,
        String displayName,
        String description
) {
    public RewardOption {
        Objects.requireNonNull(optionId, "optionId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
    }

    static RewardOption item(ItemType item, boolean owned, int currentLevel) {
        String description = owned
                ? "%s 레벨 %d → %d".formatted(item.displayName(), currentLevel, currentLevel + 1)
                : "%s를 새로 장착합니다.".formatted(item.displayName());
        return new RewardOption(
                "item:" + item.id(),
                RewardKind.ITEM,
                item.id(),
                item.displayName(),
                description);
    }

    static RewardOption upgrade(UpgradeType upgrade) {
        return new RewardOption(
                upgrade.name(),
                RewardKind.UPGRADE,
                upgrade.name(),
                upgrade.label(),
                upgrade.description());
    }

    static RewardOption evolution(EvolutionType evolution) {
        return new RewardOption(
                "evolution:" + evolution.id(),
                RewardKind.EVOLUTION,
                evolution.id(),
                evolution.displayName(),
                "%s와 %s를 소모해 진화합니다.".formatted(
                        evolution.firstMaterial().displayName(),
                        evolution.secondMaterial().displayName()));
    }
}
