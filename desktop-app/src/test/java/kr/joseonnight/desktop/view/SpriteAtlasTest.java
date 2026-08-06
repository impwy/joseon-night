package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SpriteAtlasTest {

    @Test
    void enemyKindSelectsTheMatchingSpriteAndUnknownKindsFallBackToShadowDokkaebi() {
        assertThat(SpriteAtlas.enemyAssetName("shadow-dokkaebi")).isEqualTo("enemy.png");
        assertThat(SpriteAtlas.enemyAssetName("dokkaebi-warlord"))
                .isEqualTo("dokkaebi-warlord.png");
        assertThat(SpriteAtlas.enemyAssetName("unknown-enemy")).isEqualTo("enemy.png");
        assertThat(SpriteAtlas.enemyAssetName(null)).isEqualTo("enemy.png");

        SpriteAtlas atlas = new SpriteAtlas();
        assertThat(atlas.enemy("unknown-enemy"))
                .isSameAs(atlas.enemy("shadow-dokkaebi"));
        assertThat(atlas.enemy("dokkaebi-warlord"))
                .isNotNull()
                .isNotSameAs(atlas.enemy("shadow-dokkaebi"));
    }

    @Test
    void dokkaebiWarlordIsADistinctTransparentThirtyTwoPixelSprite() throws IOException {
        BufferedImage warlord = readSprite("dokkaebi-warlord.png");
        BufferedImage shadowDokkaebi = readSprite("enemy.png");

        assertThat(warlord.getWidth()).isEqualTo(32);
        assertThat(warlord.getHeight()).isEqualTo(32);
        assertThat(warlord.getColorModel().hasAlpha()).isTrue();
        assertThat(alpha(warlord, 0, 0)).isZero();
        assertThat(alpha(warlord, 31, 0)).isZero();
        assertThat(alpha(warlord, 0, 31)).isZero();
        assertThat(alpha(warlord, 31, 31)).isZero();

        int differentPixels = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                if (warlord.getRGB(x, y) != shadowDokkaebi.getRGB(x, y)) {
                    differentPixels++;
                }
            }
        }
        assertThat(differentPixels).isPositive();
    }

    private static BufferedImage readSprite(String name) throws IOException {
        String resourcePath = "/assets/sprites/" + name;
        try (InputStream resource = SpriteAtlasTest.class.getResourceAsStream(resourcePath)) {
            assertThat(resource).as(resourcePath).isNotNull();
            BufferedImage image = ImageIO.read(resource);
            assertThat(image).as(resourcePath).isNotNull();
            return image;
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
