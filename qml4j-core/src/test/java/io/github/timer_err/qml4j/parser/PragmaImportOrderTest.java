package io.github.timer_err.qml4j.parser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
class PragmaImportOrderTest {
    @Test void pragmaThenImport() {
        assertDoesNotThrow(() -> Qml4j.parse(
            "pragma Singleton\nimport QtQuick\nQtObject { property int n: 1 }"));
    }
    @Test void importThenPragma() {
        assertDoesNotThrow(() -> Qml4j.parse(
            "import QtQuick\npragma Singleton\nQtObject { property int n: 1 }"));
    }
    @Test void interleaved() {
        assertDoesNotThrow(() -> Qml4j.parse(
            "import QtQuick\npragma Singleton\nimport QtQuick.Controls\nQtObject { property int n: 1 }"));
    }
}
