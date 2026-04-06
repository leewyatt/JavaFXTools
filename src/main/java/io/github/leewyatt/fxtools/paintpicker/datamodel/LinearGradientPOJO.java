package io.github.leewyatt.fxtools.paintpicker.datamodel;

import java.util.List;

public record LinearGradientPOJO(
        double startX, double startY, double endX, double endY,
        boolean proportional, String cycleMethod, List<StopPOJO> stops) {
}
