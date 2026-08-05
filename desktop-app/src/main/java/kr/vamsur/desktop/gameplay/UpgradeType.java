package kr.vamsur.desktop.gameplay;

/**
 * Upgrade identifier and desktop display copy.
 */
public enum UpgradeType {
    TALISMAN_DAMAGE("부적 위력", "봉인 부적의 피해량이 10 증가합니다."),
    TALISMAN_COOLDOWN("부적 속도", "봉인 부적의 공격 주기가 18% 감소합니다."),
    TALISMAN_PROJECTILES("부적 추가", "한 번에 발사하는 봉인 부적이 1개 증가합니다."),
    MOVEMENT_SPEED("발놀림", "이동 속도가 10% 증가합니다."),
    SOUL_MAGNET("혼불 자석", "혼불을 끌어당기는 범위가 30 증가합니다.");

    private final String label;
    private final String description;

    UpgradeType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
