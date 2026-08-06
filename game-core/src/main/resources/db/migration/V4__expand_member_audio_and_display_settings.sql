ALTER TABLE member_settings
    DROP CONSTRAINT ck_member_settings_master_volume;

ALTER TABLE member_settings
    RENAME COLUMN master_volume TO music_volume;

ALTER TABLE member_settings
    ADD COLUMN effects_volume INTEGER;

ALTER TABLE member_settings
    ADD COLUMN target_fps VARCHAR(20) DEFAULT 'FPS_60';

UPDATE member_settings
SET effects_volume = music_volume,
    target_fps = 'FPS_60';

ALTER TABLE member_settings
    ALTER COLUMN effects_volume SET NOT NULL;

ALTER TABLE member_settings
    ALTER COLUMN target_fps SET NOT NULL;

ALTER TABLE member_settings
    ADD CONSTRAINT ck_member_settings_music_volume
        CHECK (music_volume BETWEEN 0 AND 100);

ALTER TABLE member_settings
    ADD CONSTRAINT ck_member_settings_effects_volume
        CHECK (effects_volume BETWEEN 0 AND 100);

ALTER TABLE member_settings
    ADD CONSTRAINT ck_member_settings_target_fps
        CHECK (target_fps IN ('AUTO', 'FPS_30', 'FPS_60'));
