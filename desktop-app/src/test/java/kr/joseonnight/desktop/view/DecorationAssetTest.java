package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class DecorationAssetTest {
    private static final List<String> ASSETS = List.of(
            "decoration-dry-grass.png",
            "decoration-rubble.png",
            "decoration-ground-crack.png");

    @Test
    void decorationsAreTransparentThirtyTwoPixelPngs() throws IOException {
        for (String asset : ASSETS) {
            String resourcePath = "/assets/sprites/" + asset;
            try (InputStream resource = DecorationAssetTest.class.getResourceAsStream(resourcePath)) {
                assertThat(resource).as(resourcePath).isNotNull();
                BufferedImage image = ImageIO.read(resource);
                assertThat(image).as(resourcePath).isNotNull();
                assertThat(image.getWidth()).as(resourcePath + " width").isEqualTo(32);
                assertThat(image.getHeight()).as(resourcePath + " height").isEqualTo(32);
                assertThat(image.getColorModel().hasAlpha()).as(resourcePath + " alpha").isTrue();
                assertThat(alpha(image, 0, 0)).as(resourcePath + " top-left").isZero();
                assertThat(alpha(image, 31, 0)).as(resourcePath + " top-right").isZero();
                assertThat(alpha(image, 0, 31)).as(resourcePath + " bottom-left").isZero();
                assertThat(alpha(image, 31, 31)).as(resourcePath + " bottom-right").isZero();
            }
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
