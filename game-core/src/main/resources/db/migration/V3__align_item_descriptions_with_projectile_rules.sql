UPDATE items
SET description = CASE id
    WHEN 'seal-talisman' THEN '가장 가까운 적에게 봉인 부적 투사체를 발사한다.'
    WHEN 'flame-fan' THEN '넓은 각도로 여러 화염 투사체를 발사한다.'
    WHEN 'exorcist-sword' THEN '피해가 높은 검기 투사체를 발사한다.'
    WHEN 'returning-boomerang' THEN '좁은 각도로 초승달 투사체를 발사한다.'
    WHEN 'thunder-bell' THEN '공격 주기가 길고 피해가 높은 낙뢰 투사체를 발사한다.'
    WHEN 'spirit-gourd' THEN '넓게 퍼지는 혼령 투사체를 발사한다.'
    ELSE description
END
WHERE id IN (
    'seal-talisman',
    'flame-fan',
    'exorcist-sword',
    'returning-boomerang',
    'thunder-bell',
    'spirit-gourd'
);
