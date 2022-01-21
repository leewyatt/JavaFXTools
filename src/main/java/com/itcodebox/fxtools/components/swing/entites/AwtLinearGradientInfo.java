package com.itcodebox.fxtools.components.swing.entites;

import java.awt.*;

/**
 * @author LeeWyatt
 */
public class AwtLinearGradientInfo {
    private float[] fractions;
    private Color[] colors;

    public AwtLinearGradientInfo() {
    }

    public AwtLinearGradientInfo(float[] fractions, Color[] colors) {
        this.fractions = fractions;
        this.colors = colors;
    }

    public float[] getFractions() {
        return fractions;
    }

    public void setFractions(float[] fractions) {
        this.fractions = fractions;
    }

    public Color[] getColors() {
        return colors;
    }

    public void setColors(Color[] colors) {
        this.colors = colors;
    }
}
