package eu.amadeos.apps.imageeditor.domain;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Undo and redo without a limit. The original picture is always kept; the picture after each step is kept as a
 * soft reference, which Java drops when memory runs short. A dropped picture is made again by applying the steps
 * to the nearest picture still kept, at worst to the original. Pictures share the layers a step did not change,
 * so a brush stroke on one layer costs the memory of that layer only.
 */
public final class History {
    private final Picture original;
    private final BiFunction<Picture, Edit, Picture> apply;
    private final List<Edit> edits = new ArrayList<>();
    private final List<SoftReference<Picture>> results = new ArrayList<>();
    /** How many of the edits are in effect; the rest can be redone. */
    private int position;
    /** The position that was saved last, or -1 if the saved state was undone away. */
    private int savedAt;
    /** The current picture, held strongly. */
    private Picture current;

    public History(Picture original, BiFunction<Picture, Edit, Picture> apply) {
        this.original = original;
        this.apply = apply;
        this.current = original;
    }

    public Picture original() {
        return original;
    }

    public Picture current() {
        return current;
    }

    // --8<-- [start:push]
    /** Applies a step; what could be redone is gone. */
    public Picture push(Edit edit) {
        Picture result = apply.apply(current, edit);
        while (edits.size() > position) {
            edits.removeLast();
            results.removeLast();
        }
        if (savedAt > position) savedAt = -1;
        edits.add(edit);
        results.add(new SoftReference<>(result));
        position++;
        current = result;
        return result;
    }
    // --8<-- [end:push]

    public boolean canUndo() {
        return position > 0;
    }

    public boolean canRedo() {
        return position < edits.size();
    }

    /** The step Undo would take back. */
    public Optional<Edit> undoable() {
        return canUndo() ? Optional.of(edits.get(position - 1)) : Optional.empty();
    }

    /** The step Redo would apply again. */
    public Optional<Edit> redoable() {
        return canRedo() ? Optional.of(edits.get(position)) : Optional.empty();
    }

    public Picture undo() {
        if (!canUndo()) throw new IllegalStateException("nothing to undo");
        position--;
        current = pictureAt(position);
        return current;
    }

    public Picture redo() {
        if (!canRedo()) throw new IllegalStateException("nothing to redo");
        position++;
        current = pictureAt(position);
        return current;
    }

    /** The steps in effect, oldest first. */
    public List<Edit> edits() {
        return List.copyOf(edits.subList(0, position));
    }

    /** True if something changed since the picture was opened or last saved. */
    public boolean modified() {
        return position != savedAt;
    }

    public void markSaved() {
        savedAt = position;
    }

    // --8<-- [start:only-turns]
    /**
     * The quarter turns and flips of all steps, if there are no other steps: then a JPEG can be saved without
     * loss by jpegtran.
     */
    public Optional<Orientation> onlyTurns() {
        Orientation total = Orientation.NORMAL;
        for (Edit edit : edits()) {
            if (!(edit instanceof Edit.Turn turn)) return Optional.empty();
            total = total.then(turn.orientation());
        }
        return Optional.of(total);
    }
    // --8<-- [end:only-turns]

    // --8<-- [start:replay]
    private Picture pictureAt(int index) {
        if (index == 0) return original;
        int start = index;
        Picture picture = null;
        while (start > 0 && (picture = results.get(start - 1).get()) == null) start--;
        if (start == 0) picture = original;
        for (int i = start; i < index; i++) {
            picture = apply.apply(picture, edits.get(i));
            results.set(i, new SoftReference<>(picture));
        }
        return picture;
    }
    // --8<-- [end:replay]

    /** Drops every remembered picture except the original, as if memory had run short; for tests. */
    void forget() {
        for (SoftReference<Picture> r : results) r.clear();
    }
}
