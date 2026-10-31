package eu.amadeos.apps.imageeditor.domain;

/** The outline of a shape: its width in picture pixels and whether it is solid, dashed or dotted. */
public record LineStyle(int width, Dash dash) {
    public static final int MAX_WIDTH = 200;
    public static final LineStyle DEFAULT = new LineStyle(8, Dash.SOLID);

    public enum Dash { SOLID, DASHED, DOTTED }

    public LineStyle {
        if (width < 1 || width > MAX_WIDTH) throw new IllegalArgumentException("line width out of range: " + width);
        if (dash == null) throw new IllegalArgumentException("no dash");
    }
}
