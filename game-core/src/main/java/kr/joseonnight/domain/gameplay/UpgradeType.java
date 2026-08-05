package kr.joseonnight.domain.gameplay;

public enum UpgradeType {
    ITEM_DAMAGE("아이템 위력", "장착한 모든 아이템의 피해량이 10 증가합니다."),
    ITEM_COOLDOWN("아이템 공격 주기", "장착한 모든 아이템의 공격 주기가 18% 감소합니다."),
    ITEM_PROJECTILES("아이템 투사체 수", "장착한 모든 아이템의 투사체 수가 1개 증가합니다."),
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
