package kr.joseonnight.desktop.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DecorationLayoutTest {
    @Test
    void placementIsStableForPositiveAndNegativeWorldCells() {
        for (int cellY = -20; cellY <= 20; cellY++) {
            for (int cellX = -20; cellX <= 20; cellX++) {
                assertThat(DecorationLayout.placementAt(cellX, cellY))
                        .isEqualTo(DecorationLayout.placementAt(cellX, cellY));
            }
        }
    }

    @Test
    void densityRemainsCloseToThirtyPercentAcrossAWorldRegion() {
        int occupied = 0;
        int total = 0;
        for (int cellY = -50; cellY < 50; cellY++) {
            for (int cellX = -50; cellX < 50; cellX++) {
                total++;
                if (DecorationLayout.placementAt(cellX, cellY) != null) {
                    occupied++;
                }
            }
        }

        assertThat((double) occupied / total).isBetween(0.28, 0.32);
    }

    @Test
    void selectedDecorationStaysInsideItsWorldCell() {
        for (int cellY = -20; cellY <= 20; cellY++) {
            for (int cellX = -20; cellX <= 20; cellX++) {
                DecorationLayout.Placement placement = DecorationLayout.placementAt(cellX, cellY);
                if (placement == null) {
                    continue;
                }
                assertThat(placement.worldX()).isBetween(
                        (double) cellX * DecorationLayout.CELL_SIZE,
                        (double) (cellX + 1) * DecorationLayout.CELL_SIZE);
                assertThat(placement.worldY()).isBetween(
                        (double) cellY * DecorationLayout.CELL_SIZE,
                        (double) (cellY + 1) * DecorationLayout.CELL_SIZE);
            }
        }
    }
}
