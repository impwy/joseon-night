package kr.joseonnight.domain.gameplay;

/**
 * Stable protocol values for one-shot game sounds.
 */
public enum SoundCue {
    LEVEL_UP,
    GUARD,
    CHEST_OPENED,
    DEFEAT,
    SEAL_TALISMAN_ATTACK,
    FLAME_FAN_ATTACK,
    EXORCIST_SWORD_ATTACK,
    RETURNING_BOOMERANG_ATTACK,
    THUNDER_BELL_ATTACK,
    SPIRIT_GOURD_ATTACK,
    TEN_THOUSAND_SEAL_ARRAY_ATTACK,
    HEAVENLY_THUNDER_SEAL_ATTACK,
    INFERNO_RETURNING_WHEEL_ATTACK,
    BLUE_FLAME_SPIRIT_GOURD_ATTACK,
    LUNAR_ECLIPSE_TWIN_BLADES_ATTACK,
    THUNDER_FLAME_DIVINE_ORB_ATTACK;

    public static SoundCue forItem(ItemType item) {
        return switch (item) {
            case SEAL_TALISMAN -> SEAL_TALISMAN_ATTACK;
            case FLAME_FAN -> FLAME_FAN_ATTACK;
            case EXORCIST_SWORD -> EXORCIST_SWORD_ATTACK;
            case RETURNING_BOOMERANG -> RETURNING_BOOMERANG_ATTACK;
            case THUNDER_BELL -> THUNDER_BELL_ATTACK;
            case SPIRIT_GOURD -> SPIRIT_GOURD_ATTACK;
        };
    }

    public static SoundCue forEvolution(EvolutionType evolution) {
        return switch (evolution) {
            case TEN_THOUSAND_SEAL_ARRAY -> TEN_THOUSAND_SEAL_ARRAY_ATTACK;
            case HEAVENLY_THUNDER_SEAL -> HEAVENLY_THUNDER_SEAL_ATTACK;
            case INFERNO_RETURNING_WHEEL -> INFERNO_RETURNING_WHEEL_ATTACK;
            case BLUE_FLAME_SPIRIT_GOURD -> BLUE_FLAME_SPIRIT_GOURD_ATTACK;
            case LUNAR_ECLIPSE_TWIN_BLADES -> LUNAR_ECLIPSE_TWIN_BLADES_ATTACK;
            case THUNDER_FLAME_DIVINE_ORB -> THUNDER_FLAME_DIVINE_ORB_ATTACK;
        };
    }
}
