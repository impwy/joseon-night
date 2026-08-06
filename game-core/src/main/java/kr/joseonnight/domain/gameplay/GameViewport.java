package kr.joseonnight.domain.gameplay;

import org.springframework.util.Assert;

/**
 * Logical pixel dimensions used for server-side visibility decisions.
 */
public record GameViewport(int width, int height) {

    public static final int DEFAULT_WIDTH = 1_280;
    public static final int DEFAULT_HEIGHT = 720;
    public static final int MIN_WIDTH = 640;
    public static final int MIN_HEIGHT = 360;
    public static final int MAX_WIDTH = 3_840;
    public static final int MAX_HEIGHT = 2_160;

    public GameViewport {
        Assert.isTrue(width >= MIN_WIDTH && width <= MAX_WIDTH,
                "width must be between " + MIN_WIDTH + " and " + MAX_WIDTH);
        Assert.isTrue(height >= MIN_HEIGHT && height <= MAX_HEIGHT,
                "height must be between " + MIN_HEIGHT + " and " + MAX_HEIGHT);
    }

    public static GameViewport standard() {
        return new GameViewport(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    boolean intersectsCircle(
            double centerX,
            double centerY,
            double circleX,
            double circleY,
            double radius
    ) {
        double halfWidth = width / 2.0;
        double halfHeight = height / 2.0;
        double nearestX = Math.max(
                centerX - halfWidth,
                Math.min(circleX, centerX + halfWidth));
        double nearestY = Math.max(
                centerY - halfHeight,
                Math.min(circleY, centerY + halfHeight));
        double horizontal = circleX - nearestX;
        double vertical = circleY - nearestY;
        return horizontal * horizontal + vertical * vertical <= radius * radius;
    }
}
