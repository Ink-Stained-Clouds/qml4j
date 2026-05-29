package io.qml4j.render.items;

import io.qml4j.engine.Signal;
import io.qml4j.engine.binding.Property;

import java.util.List;

public class TextEdit extends Item {
    public final Property<String> text = new Property<>("");
    public final Property<String> color = new Property<>("#000000");
    public final Property<String> cursorColor = new Property<>("#000000");
    public final Property<Number> fontSize = new Property<>(16);
    public final Property<Number> cursorPosition = new Property<>(0);
    public final Property<Number> selectionStart = new Property<>(0);
    public final Property<Number> selectionEnd = new Property<>(0);
    public final Property<String> selectionColor = new Property<>("#308cff");
    public final Property<Boolean> readOnly = new Property<>(Boolean.FALSE);
    public final Property<String> wrapMode = new Property<>("NoWrap");
    public final Property<String> verticalAlignment = new Property<>("AlignTop");
    public final Property<Number> lineCount = new Property<>(1);

    public final Signal textChanged = new Signal();

    public int selectionAnchor = -1;

    public List<String> cachedLines;
    public int[] cachedStarts;
    public String cachedText;
    public String cachedWrap;
    public float cachedWidth;
    public float cachedFontSize;
}
