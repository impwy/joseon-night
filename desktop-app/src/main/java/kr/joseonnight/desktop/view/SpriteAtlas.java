package kr.joseonnight.desktop.view;

import java.io.IOException;
import java.io.InputStream;
import javafx.scene.image.Image;

final class SpriteAtlas {
    private static final String ROOT = "/assets/sprites/";

    private final Image player = load("player.png");
    private final Image galeMaiden = load("gale-maiden.png");
    private final Image enemy = load("enemy.png");
    private final Image talisman = load("talisman.png");
    private final Image flameFan = load("flame-fan.png");
    private final Image wardingSword = load("warding-sword.png");
    private final Image returningBoomerang = load("returning-boomerang.png");
    private final Image thunderBell = load("thunder-bell.png");
    private final Image spiritGourd = load("spirit-gourd.png");
    private final Image soulFlame = load("soul-flame.png");
    private final Image ground = load("ground.png");
    private final Image yellowChest = load("chest-yellow.png");
    private final Image purpleChest = load("chest-purple.png");

    Image player(String characterId) {
        if (characterId != null
                && ("GALE_MAIDEN".equalsIgnoreCase(characterId)
                        || "GALE_SHAMAN".equalsIgnoreCase(characterId)
                        || "gale-shaman".equalsIgnoreCase(characterId))
                && galeMaiden != null) {
            return galeMaiden;
        }
        return player;
    }

    Image enemy() {
        return enemy;
    }

    Image projectile(String kindId) {
        if (kindId == null) {
            return talisman;
        }
        return switch (kindId.toLowerCase(java.util.Locale.ROOT)) {
            case "flame-fan" -> flameFan;
            case "exorcist-sword" -> wardingSword;
            case "returning-boomerang" -> returningBoomerang;
            case "thunder-bell" -> thunderBell;
            case "spirit-gourd" -> spiritGourd;
            default -> talisman;
        };
    }

    Image soulFlame() {
        return soulFlame;
    }

    Image ground() {
        return ground;
    }

    Image chest(String type) {
        if (type != null && type.toUpperCase(java.util.Locale.ROOT).contains("PURPLE")) {
            return purpleChest;
        }
        return yellowChest;
    }

    private static Image load(String name) {
        try (InputStream stream = SpriteAtlas.class.getResourceAsStream(ROOT + name)) {
            if (stream == null) {
                return null;
            }
            Image image = new Image(stream, 0, 0, true, false);
            return image.isError() ? null : image;
        } catch (IOException ignored) {
            return null;
        }
    }
}
