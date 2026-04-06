package io.github.leewyatt.fxtools.paintpicker.datamodel;

import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;

public record Gradient(LinearGradientPaint linearGradient, RadialGradientPaint radialGradient) {
}
