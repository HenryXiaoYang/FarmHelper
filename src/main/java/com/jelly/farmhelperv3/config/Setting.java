package com.jelly.farmhelperv3.config;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Setting {
    enum Kind { SWITCH, DROPDOWN, NUMBER, SLIDER, TEXT, BUTTON, INFO, PAGE, HUD, COLOR, KEYBIND, DUALOPTION, CHECKBOX }
    Kind kind();
    String name() default "";
    String description() default "";
    String category() default "";
    String subcategory() default "";
    String text() default "";
    String placeholder() default "";
    String type() default "INFO";
    String location() default "BOTTOM";
    String[] options() default {};
    String left() default "Off";
    String right() default "On";
    double min() default -Double.MAX_VALUE;
    double max() default Double.MAX_VALUE;
    double step() default 1;
    int size() default 1;
    boolean multiline() default false;
    boolean secure() default false;
    boolean instant() default false;
    boolean alpha() default true;
    boolean allowAlpha() default true;
}
