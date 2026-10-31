package eu.amadeos.apps.imageeditor.domain;

import java.util.List;
import java.util.Optional;

/**
 * How a picture is turned before it is shown: one of the eight ways to lay a rectangle onto its place, each a
 * mirror image (or not) followed by a number of quarter turns clockwise. EXIF calls them orientations 1 to 8;
 * the user's rotations and flips combine with them by {@link #then}.
 *
 * <p>Each orientation is a 2 × 2 matrix on screen coordinates (x to the right, y down).
 */
public enum Orientation {
    NORMAL(0, false, 1, null),
    MIRROR(0, true, 2, "-flip horizontal"),
    ROTATE_180(2, false, 3, "-rotate 180"),
    FLIP(2, true, 4, "-flip vertical"),
    TRANSPOSE(3, true, 5, "-transpose"),
    ROTATE_90(1, false, 6, "-rotate 90"),
    TRANSVERSE(1, true, 7, "-transverse"),
    ROTATE_270(3, false, 8, "-rotate 270");

    private final int turns;
    private final boolean mirrored;
    private final int exif;
    private final String jpegtran;

    Orientation(int turns, boolean mirrored, int exif, String jpegtran) {
        this.turns = turns;
        this.mirrored = mirrored;
        this.exif = exif;
        this.jpegtran = jpegtran;
    }

    /** Quarter turns clockwise, after the mirroring. */
    public int turns() {
        return turns;
    }

    /** Mirrored left to right before the turns. */
    public boolean mirrored() {
        return mirrored;
    }

    /** The EXIF orientation, 1 to 8. */
    public int exif() {
        return exif;
    }

    /** True if width and height change places. */
    public boolean swapsSides() {
        return turns % 2 == 1;
    }

    /** The options for {@code jpegtran} that turn the pixels this way; none for {@link #NORMAL}. */
    public List<String> jpegtranOptions() {
        return jpegtran == null ? List.of() : List.of(jpegtran.split(" "));
    }

    /** The orientation of an EXIF value; unknown values count as normal. */
    public static Orientation ofExif(int value) {
        for (Orientation o : values()) {
            if (o.exif == value) return o;
        }
        return NORMAL;
    }

    /** The matrix {a, b, c, d}: x' = a x + b y, y' = c x + d y. */
    public int[] matrix() {
        // mirror: x -> -x; quarter turn clockwise: (x, y) -> (-y, x)
        int a = mirrored ? -1 : 1, b = 0, c = 0, d = 1;
        for (int i = 0; i < turns; i++) {
            int na = -c, nb = -d, nc = a, nd = b;
            a = na;
            b = nb;
            c = nc;
            d = nd;
        }
        return new int[]{a, b, c, d};
    }

    /** This orientation followed by {@code next}. */
    public Orientation then(Orientation next) {
        int[] m = matrix();
        int[] n = next.matrix();
        int[] product = {
                n[0] * m[0] + n[1] * m[2], n[0] * m[1] + n[1] * m[3],
                n[2] * m[0] + n[3] * m[2], n[2] * m[1] + n[3] * m[3]};
        return ofMatrix(product).orElseThrow();
    }

    public Orientation rotateRight() {
        return then(ROTATE_90);
    }

    public Orientation rotateLeft() {
        return then(ROTATE_270);
    }

    /** Mirrored left to right, as seen on the screen. */
    public Orientation flipHorizontally() {
        return then(MIRROR);
    }

    /** Mirrored top to bottom, as seen on the screen. */
    public Orientation flipVertically() {
        return then(FLIP);
    }

    /** The orientation that undoes this one. */
    public Orientation inverse() {
        for (Orientation o : values()) {
            if (then(o) == NORMAL) return o;
        }
        throw new IllegalStateException();
    }

    private static Optional<Orientation> ofMatrix(int[] matrix) {
        for (Orientation o : values()) {
            if (java.util.Arrays.equals(o.matrix(), matrix)) return Optional.of(o);
        }
        return Optional.empty();
    }
}
