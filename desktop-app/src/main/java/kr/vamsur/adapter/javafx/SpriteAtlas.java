package kr.vamsur.adapter.javafx;

import java.io.IOException;
import java.io.InputStream;
import javafx.scene.image.Image;

final class SpriteAtlas {
    private static final String ROOT = "/assets/sprites/";

    private final Image player = load("player.png");
    private final Image enemy = load("enemy.png");
    private final Image talisman = load("talisman.png");
    private final Image soulFlame = load("soul-flame.png");
    private final Image ground = load("ground.png");

    Image player() {
        return player;
    }

    Image enemy() {
        return enemy;
    }

    Image talisman() {
        return talisman;
    }

    Image soulFlame() {
        return soulFlame;
    }

    Image ground() {
        return ground;
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
