package com.itcodebox.fxtools.utils;

import com.itcodebox.fxtools.components.swing.entites.AwtLinearGradientInfo;

import java.awt.*;
import java.util.List;

/**
 * @author LeeWyatt
 */
public interface LinearGradientConstant {

    List<AwtLinearGradientInfo> GradientList = List.of(
            new AwtLinearGradientInfo(new float[]{0F, 0.5F, 1.0F}, new Color[]{new Color(35, 208, 243), new Color(215, 145, 249), new Color(254, 123, 132)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(187, 150, 225), new Color(92, 86, 187)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(172,202,253),new Color(254,190,235)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(238, 38, 16), new Color(247, 173, 29)}),
            new AwtLinearGradientInfo(new float[]{0.0F,0.5F,1.0F}, new Color[]{new Color(253,62,172),new Color(122,75,162),new Color(45,135,197)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(235, 203, 255), new Color(167, 58, 229)}),
            new AwtLinearGradientInfo(new float[]{0F, 0.5F, 1.0F}, new Color[]{new Color(25, 50, 55), new Color(46, 78, 88), new Color(57, 104, 124)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(126, 232, 236), new Color(74, 74, 227)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 1.0F}, new Color[]{ new Color(251, 199, 212),new Color(183, 152, 146)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 0.5F, 1.0F}, new Color[]{new Color(87, 192, 115), new Color(162, 127, 225), new Color(93, 39, 195)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(47, 141, 243), new Color(0, 238, 255)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(28, 132, 236), new Color(173, 13, 236)}),
            new AwtLinearGradientInfo(new float[]{0.0F,0.5F,1.0F}, new Color[]{new Color(118,160,210),new Color(122,203,201),new Color(232,132,176)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(249, 77, 74), new Color(51, 142, 238)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(94, 211, 247), new Color(23, 99, 198)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 0.5F, 1.0F}, new Color[]{new Color(26, 45, 109), new Color(177, 31, 30), new Color(252, 186, 46)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(211, 218, 223), new Color(61, 83, 104)}),
            new AwtLinearGradientInfo(new float[]{0F, 1.0F}, new Color[]{new Color(98, 76, 160), new Color(235, 173, 205)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 0.5F, 1.0F},new Color[]{new Color(155, 236, 252), new Color(100, 199, 248), new Color(2, 83, 213)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 1.0F}, new Color[]{new Color(250, 71, 108), new Color(64, 95, 253)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 1.0F}, new Color[]{new Color(128, 0, 253), new Color(225, 3, 250)}),
            new AwtLinearGradientInfo(new float[]{0.0F,0.5F,1.0F}, new Color[]{new Color(206,147,193),new Color(220,210,182),new Color(123,162,211)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 1.0F}, new Color[]{new Color(252, 223, 87), new Color(254, 163, 80)}),
            new AwtLinearGradientInfo(new float[]{0.0F, 1.0F}, new Color[]{new Color(150, 152, 239), new Color(250, 198, 210)}),
            new AwtLinearGradientInfo(new float[]{0.0F,0.5F,1.0F}, new Color[]{new Color(129,57,181),new Color(253,30,28),new Color(251,175,70)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(253,109,126),new Color(190,232,253)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(205,42,95),new Color(118,60,137)}),
            new AwtLinearGradientInfo(new float[]{0.0F,0.5F,1.0F}, new Color[]{new Color(65,89,209),new Color(199,80,192),new Color(254,200,113)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(194,23,2),new Color(255,200,3)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(113,222,250),new Color(252,210,150)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(250,0,253),new Color(2,220,221)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(0,198,253),new Color(253,252,29)}),
            new AwtLinearGradientInfo(new float[]{0.0F,1.0F}, new Color[]{new Color(252,223,232),new Color(182,254,252)}),
            new AwtLinearGradientInfo(new float[]{0.0F,0.5F,1.0F}, new Color[]{new Color(252,139,255),new Color(43,212,253),new Color(45,255,137)})
    );

    /**
     background-color: #FFDEE9;
     background-image: linear-gradient(0deg, #FFDEE9 0%, #B5FFFC 100%);
     background-color: #FA8BFF;
     background-image: linear-gradient(45deg, #FA8BFF 0%, #2BD2FF 52%, #2BFF88 90%);



     */
}
