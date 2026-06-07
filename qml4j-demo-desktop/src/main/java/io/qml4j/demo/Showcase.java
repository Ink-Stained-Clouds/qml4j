package io.qml4j.demo;

import java.util.Arrays;
import java.util.List;

final class Showcase {

    final String title;
    final String resource;

    private Showcase(String title, String resource) {
        this.title = title;
        this.resource = resource;
    }

    private static Showcase of(String title, String name) {
        return new Showcase(title, "/showcases/" + name + ".qml");
    }

    // MD3 component showcases first (the migration target), then the engine-feature
    // showcases. Order is the launcher order. DefaultProp/M45/QtObject are omitted:
    // they import relative-directory components (Panel, widgets/Pill, apptheme) that
    // were never migrated and don't exist in the Android assets either.
    static List<Showcase> all() {
        return Arrays.asList(
            of("Button", "ButtonShowcase"),
            of("Dialog", "DialogShowcase"),
            of("Chip", "ChipShowcase"),
            of("Checkbox", "CheckboxShowcase"),
            of("Switch / RadioButton", "SwitchRadioShowcase"),
            of("IconButton", "IconButtonShowcase"),
            of("Card", "CardShowcase"),
            of("SegmentedButton", "SegmentedButtonShowcase"),
            of("FAB", "FabShowcase"),
            of("Slider", "SliderShowcase"),
            of("Snackbar", "SnackbarShowcase"),
            of("NavigationBar", "NavigationBarShowcase"),
            of("ScrollBar", "ScrollBarShowcase"),
            of("ToolTip", "ToolTipShowcase"),
            of("Controls", "ControlsShowcase"),
            of("Composite", "CompositeShowcase"),
            of("MD3 gallery", "Md3GalleryShowcase"),
            of("Layouts", "LayoutShowcase"),
            of("GridLayout / Flow", "GridFlowShowcase"),
            of("Shapes", "ShapeShowcase"),
            of("Canvas", "CanvasShowcase"),
            of("Charts", "ChartShowcase"),
            of("Qt.color", "ColorShowcase"),
            of("LayerEffect", "LayerEffectShowcase"),
            of("Animations", "AnimShowcase"),
            of("Keys", "KeysShowcase"),
            of("FocusScope", "FocusScopeShowcase"),
            of("Qt namespace", "QtNamespaceShowcase"),
            of("ES6", "Es6Showcase"),
            of("Window", "WindowShowcase")
        );
    }
}
