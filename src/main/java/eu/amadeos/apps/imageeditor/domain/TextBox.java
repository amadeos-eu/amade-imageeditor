package eu.amadeos.apps.imageeditor.domain;

import java.awt.Color;

/**
 * The text of a text layer and how it looks. The text is placed by its middle ({@code centreX}, {@code centreY}
 * in picture pixels) and turned around it by {@code rotation} degrees clockwise, so that cropping, turning,
 * straightening and resizing the picture move the text without making pixels of it.
 *
 * @param size   the font size in picture pixels
 * @param effect drawn in {@code effectColour}: an outline round the letters, a shadow, or a box behind the text
 */
public record TextBox(String text, String font, float size, boolean bold, boolean italic, Align align,
                      Color colour, Effect effect, Color effectColour, double centreX, double centreY,
                      double rotation) {
    public static final float MIN_SIZE = 4;
    public static final float MAX_SIZE = 2000;

    public enum Align { LEFT, CENTRE, RIGHT }

    public enum Effect { NONE, OUTLINE, SHADOW, BOX }

    public TextBox {
        if (text == null || font == null || align == null || colour == null || effect == null || effectColour == null) {
            throw new IllegalArgumentException("missing part of a text box");
        }
        if (!(size >= MIN_SIZE && size <= MAX_SIZE)) throw new IllegalArgumentException("font size out of range: " + size);
        rotation = normalize(rotation);
    }

    /** Brings an angle into -180 (exclusive) .. 180. */
    static double normalize(double degrees) {
        double d = degrees % 360;
        if (d <= -180) d += 360;
        if (d > 180) d -= 360;
        return d;
    }

    public TextBox withText(String newText) {
        return new TextBox(newText, font, size, bold, italic, align, colour, effect, effectColour, centreX, centreY,
                rotation);
    }

    public TextBox withCentre(double x, double y) {
        return new TextBox(text, font, size, bold, italic, align, colour, effect, effectColour, x, y, rotation);
    }

    public TextBox withSize(float newSize) {
        return new TextBox(text, font, Math.clamp(newSize, MIN_SIZE, MAX_SIZE), bold, italic, align, colour, effect,
                effectColour, centreX, centreY, rotation);
    }

    public TextBox withRotation(double degrees) {
        return new TextBox(text, font, size, bold, italic, align, colour, effect, effectColour, centreX, centreY,
                degrees);
    }

    /** The same look with another text place and text: what the Text tool keeps for the next text. */
    public TextBox withStyleOf(TextBox other) {
        return new TextBox(text, other.font, other.size, other.bold, other.italic, other.align, other.colour,
                other.effect, other.effectColour, centreX, centreY, rotation);
    }

    /** The first line, shortened, for the layer's name. */
    public String title() {
        String first = text.strip().lines().findFirst().orElse("").strip();
        return first.length() <= 32 ? first : first.substring(0, 31) + "…";
    }

    /** True if there is nothing to show. */
    public boolean blank() {
        return text.isBlank();
    }
}
