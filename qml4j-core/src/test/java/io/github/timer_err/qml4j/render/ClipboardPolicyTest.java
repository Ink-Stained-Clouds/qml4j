package io.github.timer_err.qml4j.render;

import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.items.core.Item;
import io.github.timer_err.qml4j.render.items.input.TextEdit;
import io.github.timer_err.qml4j.render.items.input.TextInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardPolicyTest {

    private static QmlView newView() {
        return QmlView.withStockTypes(new QmlEngine());
    }

    @Test
    void copyAndCutAreBlockedForEveryNonNormalEchoMode() {
        // 1/2/3 are NoEcho, Password and PasswordEchoOnEdit; 99 stands for any
        // future or malformed mode, which must fail closed rather than leak.
        // readOnly is crossed in because copy() has no read-only guard of its own:
        // for a read-only masked field the policy is the only thing refusing it.
        for (boolean readOnly : new boolean[] {false, true}) {
            for (int mode : new int[] {1, 2, 3, 99}) {
                String label = "echoMode=" + mode + ", readOnly=" + readOnly;
                QmlView v = newView();
                RecordingClipboard cb = new RecordingClipboard();
                v.setClipboard(cb);
                Item root = v.load(
                    "Item { width: 200; height: 100\n"
                        + "  TextInput { focus: true; text: \"s3cret\"; echoMode: " + mode
                        + "; readOnly: " + readOnly + " }\n"
                        + "}");
                TextInput ti = (TextInput) root.children.get(0);
                ti.selectionStart.set(0);
                ti.selectionEnd.set(6);
                ti.selectionAnchor = 0;
                ti.cursorPosition.set(6);
                int[] changes = new int[1];
                ti.textChanged.connect(() -> changes[0]++);

                assertFalse(v.copy(), label + ": copy must be refused");
                assertFalse(v.cut(), label + ": cut must be refused");
                assertEquals(0, cb.setCalls, label + ": nothing may be handed to the clipboard");
                assertEquals(0, cb.getCalls, label + ": the clipboard must not be read either");
                assertNull(cb.stored, label + ": the clipboard must stay untouched");
                assertEquals("s3cret", ti.text.peek(), label + ": the text must survive");
                assertEquals(0, ti.selectionStart.peekInt(), label + ": selection start must survive");
                assertEquals(6, ti.selectionEnd.peekInt(), label + ": selection end must survive");
                assertEquals(0, ti.selectionAnchor, label + ": the selection anchor must survive");
                assertEquals(6, ti.cursorPosition.peekInt(), label + ": the caret must survive");
                assertEquals(0, changes[0], label + ": textChanged must not fire");
            }
        }
    }

    @Test
    void copyAndCutFailClosedForInvalidEchoModeSetInQml() {
        // Each value reaches echoMode raw through the real compiler: 0.5 / null / false
        // are one-token literals assigned straight to the Property, while -0.5 and NaN
        // are not literals (a leading '-', a bare identifier) and arrive via a binding.
        // peekInt() folds every one of them to 0, which would wrongly permit the copy.
        String[][] cases = {
            {"literal 0.5", "echoMode: 0.5"},
            {"literal null", "echoMode: null"},
            {"literal false", "echoMode: false"},
            {"binding -0.5", "echoMode: -0.5"},
            {"binding NaN", "echoMode: NaN"},
        };
        for (String[] c : cases) {
            QmlView v = newView();
            RecordingClipboard cb = new RecordingClipboard();
            v.setClipboard(cb);
            Item root = v.load(
                "Item { width: 200; height: 100\n"
                    + "  TextInput { focus: true; text: \"s3cret\"; " + c[1] + " }\n"
                    + "}");
            TextInput ti = (TextInput) root.children.get(0);
            assertCopyAndCutFailClosed(v, ti, cb, c[0]);
        }
    }

    @Test
    void copyAndCutFailClosedForInvalidEchoModeSetOnTheProperty() {
        // The imperative property write the other tests use (Property.set) stores the
        // value verbatim, so a fractional, NaN or null mode reaches the policy as written.
        Number[] modes = {0.5, -0.5, Double.NaN, null};
        for (Number mode : modes) {
            QmlView v = newView();
            RecordingClipboard cb = new RecordingClipboard();
            v.setClipboard(cb);
            Item root = v.load(
                "Item { width: 200; height: 100\n"
                    + "  TextInput { focus: true; text: \"s3cret\" }\n"
                    + "}");
            TextInput ti = (TextInput) root.children.get(0);
            ti.echoMode.set(mode);
            assertCopyAndCutFailClosed(v, ti, cb, "echoMode=" + mode);
        }
    }

    @Test
    void copyAndCutFailClosedForEchoModeAssignedFromJs() {
        // Imperative JS assignment routes through MemberAccess.writeMember -> Property.set,
        // the exact path a QML handler takes, landing the raw JS number on echoMode.
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n"
                + "  TextInput { id: inp; focus: true; text: \"s3cret\"\n"
                + "    Component.onCompleted: inp.echoMode = 0.5 }\n"
                + "}");
        TextInput ti = (TextInput) root.children.get(0);
        assertEquals(0.5, ti.echoMode.peekDouble(), 0d, "the JS write must land 0.5 verbatim");
        assertCopyAndCutFailClosed(v, ti, cb, "js echoMode = 0.5");
    }

    @Test
    void floatingPointZeroEchoModeStillCopies() {
        // The permitted set is exactly {0, 0.0, -0.0}: a Double zero is still Normal and
        // must keep copying, so the guard cannot be "refuse anything that is not an int".
        for (Number zero : new Number[] {0.0, -0.0}) {
            QmlView v = newView();
            RecordingClipboard cb = new RecordingClipboard();
            v.setClipboard(cb);
            Item root = v.load(
                "Item { width: 200; height: 100\n"
                    + "  TextInput { focus: true; text: \"hello world\" }\n"
                    + "}");
            TextInput ti = (TextInput) root.children.get(0);
            ti.echoMode.set(zero);
            ti.selectionStart.set(0);
            ti.selectionEnd.set(5);
            assertTrue(v.copy(), "echoMode " + zero + " is Normal and must copy");
            assertEquals("hello", cb.stored, "echoMode " + zero + " must publish the selection");
        }
    }

    private static void assertCopyAndCutFailClosed(
        QmlView v, TextInput ti, RecordingClipboard cb, String label) {
        ti.selectionStart.set(0);
        ti.selectionEnd.set(6);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(6);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        assertFalse(v.copy(), label + ": copy must be refused");
        assertFalse(v.cut(), label + ": cut must be refused");
        assertEquals(0, cb.setCalls, label + ": nothing may be handed to the clipboard");
        assertEquals(0, cb.getCalls, label + ": the clipboard must not be read either");
        assertNull(cb.stored, label + ": the clipboard must stay untouched");
        assertEquals("s3cret", ti.text.peek(), label + ": the text must survive");
        assertEquals(0, ti.selectionStart.peekInt(), label + ": selection start must survive");
        assertEquals(6, ti.selectionEnd.peekInt(), label + ": selection end must survive");
        assertEquals(0, ti.selectionAnchor, label + ": the selection anchor must survive");
        assertEquals(6, ti.cursorPosition.peekInt(), label + ": the caret must survive");
        assertEquals(0, changes[0], label + ": textChanged must not fire");
    }

    @Test
    void normalEchoModeStillCopiesAndCuts() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        assertTrue(v.copy(), "a Normal input must copy");
        assertEquals("hello", cb.stored, "copy must publish the selected text");
        assertEquals("hello world", ti.text.peek(), "copy must not edit the text");
        assertEquals(0, changes[0], "copy must not emit textChanged");

        assertTrue(v.cut(), "a Normal input must cut");
        assertEquals("hello", cb.stored, "cut must publish the selected text");
        assertEquals(" world", ti.text.peek(), "cut must remove the selection");
        assertEquals(0, ti.cursorPosition.peekInt(), "cut must leave the caret at the selection start");
        assertEquals(0, ti.selectionStart.peekInt(), "cut must clear the selection start");
        assertEquals(0, ti.selectionEnd.peekInt(), "cut must clear the selection end");
        assertEquals(-1, ti.selectionAnchor, "cut must clear the selection anchor");
        assertEquals(1, changes[0], "cut must emit textChanged exactly once");
    }

    @Test
    void pasteRemainsAllowedForNonNormalEchoMode() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.stored = "XY";
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"ab\"; cursorPosition: 1; echoMode: 1 }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);

        assertTrue(v.paste(), "a password field must still accept a paste");
        assertEquals("aXYb", ti.text.peek(), "paste must insert at the caret");
    }

    @Test
    void textEditRetainsDefaultClipboardCopyPolicy() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextEdit { focus: true; text: \"note body\" }\n" +
            "}");
        TextEdit te = (TextEdit) root.children.get(0);
        te.selectionStart.set(0);
        te.selectionEnd.set(4);
        te.cursorPosition.set(4);

        assertTrue(v.copy(), "TextEdit must inherit the permissive default policy");
        assertEquals("note", cb.stored, "TextEdit copy must publish the selection");
        assertTrue(v.cut(), "TextEdit must still cut");
        assertEquals(" body", te.text.peek(), "TextEdit cut must remove the selection");
    }

    @Test
    void copyAndCutReturnFalseWithoutClipboard() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        assertFalse(v.copy(), "copy without a clipboard must fail");
        assertFalse(v.cut(), "cut without a clipboard must fail");
        assertEquals("hello world", ti.text.peek(), "an unbacked cut must not delete the selection");
        assertEquals(0, ti.selectionStart.peekInt(), "selection start must survive");
        assertEquals(5, ti.selectionEnd.peekInt(), "selection end must survive");
        assertEquals(0, ti.selectionAnchor, "the selection anchor must survive");
        assertEquals(5, ti.cursorPosition.peekInt(), "the caret must survive");
        assertEquals(0, changes[0], "textChanged must not fire");
    }

    @Test
    void copyReturnsFalseWhenClipboardWriteCannotBeConfirmed() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.stored = "older clipboard entry";
        cb.ignoreWrites = true;
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);

        assertFalse(v.copy(), "copy must not claim success when the write is unconfirmed");
        assertEquals("hello world", ti.text.peek(), "a refused copy must not edit the text");
        assertEquals("older clipboard entry", cb.stored, "the backend kept its previous value");
        assertEquals(1, cb.setCalls, "the write must have been attempted once");
        assertEquals(1, cb.getCalls, "the write must have been read back once");
    }

    @Test
    void cutDoesNotMutateWhenClipboardWriteCannotBeConfirmed() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.stored = "older clipboard entry";
        cb.ignoreWrites = true;
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        assertFalse(v.cut(), "cut must not claim success when the write is unconfirmed");
        assertEquals("hello world", ti.text.peek(), "an unconfirmed cut must not delete the selection");
        assertEquals(0, ti.selectionStart.peekInt(), "selection start must survive");
        assertEquals(5, ti.selectionEnd.peekInt(), "selection end must survive");
        assertEquals(0, ti.selectionAnchor, "the selection anchor must survive");
        assertEquals(5, ti.cursorPosition.peekInt(), "the caret must survive");
        assertEquals(0, changes[0], "textChanged must not fire");
    }

    @Test
    void cutSucceedsWhenTheClipboardAlreadyHoldsTheSelection() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.stored = "hello";
        cb.ignoreWrites = true;
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);

        // The postcondition is a statement about the clipboard's contents, not about
        // the write having had an effect, so a dropped write over an already-equal
        // value is still a safe cut.
        assertTrue(v.cut(), "an already-satisfied postcondition must still permit the cut");
        assertEquals(" world", ti.text.peek(), "the confirmed cut must remove the selection");
        assertEquals("hello", cb.stored, "the selection is on the clipboard however it got there");
    }

    @Test
    void cutIsRefusedWhenTheBackendRewritesWhatItStored() {
        // The readback comparison is exact on purpose. A backend that normalises line
        // endings, or truncates at an embedded NUL, never stored the selection, and
        // nothing tells such a rewrite apart from an unrelated value, so the cut has to
        // fail closed rather than delete text that was never published.
        assertRewritingBackendRefusesCut(
            "a\r\nb world", s -> s.replace("\r\n", "\n"), "a\nb", "CRLF normalised");
        assertRewritingBackendRefusesCut(
            "a\0b world", s -> s.substring(0, s.indexOf('\0')), "a", "truncated at NUL");
    }

    private static void assertRewritingBackendRefusesCut(
        String text, UnaryOperator<String> rewrite, String expectedStored, String label) {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.rewrite = rewrite;
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.text.set(text);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(4);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(4);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        assertFalse(v.cut(), label + ": a rewritten readback must not confirm the write");
        assertEquals(expectedStored, cb.stored, label + ": the backend stored its own version");
        assertEquals(text, ti.text.peek(), label + ": the selection must survive");
        assertEquals(0, ti.selectionStart.peekInt(), label + ": selection start must survive");
        assertEquals(4, ti.selectionEnd.peekInt(), label + ": selection end must survive");
        assertEquals(0, ti.selectionAnchor, label + ": the selection anchor must survive");
        assertEquals(4, ti.cursorPosition.peekInt(), label + ": the caret must survive");
        assertEquals(0, changes[0], label + ": textChanged must not fire");
    }

    @Test
    void cutDoesNotMutateWhenSetThrows() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.setFailure = new IllegalStateException("clipboard write failed");
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        IllegalStateException thrown =
            assertThrows(IllegalStateException.class, v::cut, "the backend failure must propagate");
        assertEquals("clipboard write failed", thrown.getMessage(), "the original failure must survive");
        assertEquals(1, cb.setCalls, "the write must have been attempted once");
        assertEquals(0, cb.getCalls, "a failed write must not be read back");
        assertEquals("hello world", ti.text.peek(), "a failed cut must not delete the selection");
        assertEquals(0, ti.selectionStart.peekInt(), "selection start must survive");
        assertEquals(5, ti.selectionEnd.peekInt(), "selection end must survive");
        assertEquals(0, ti.selectionAnchor, "the selection anchor must survive");
        assertEquals(5, ti.cursorPosition.peekInt(), "the caret must survive");
        assertEquals(0, changes[0], "textChanged must not fire");
    }

    @Test
    void cutDoesNotMutateWhenReadbackThrows() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.getFailure = new IllegalStateException("clipboard read failed");
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        IllegalStateException thrown =
            assertThrows(IllegalStateException.class, v::cut, "the backend failure must propagate");
        assertEquals("clipboard read failed", thrown.getMessage(), "the original failure must survive");
        assertEquals(1, cb.setCalls, "the write must have been attempted once");
        assertEquals(1, cb.getCalls, "the read back must have been attempted once");
        assertEquals("hello world", ti.text.peek(), "a failed cut must not delete the selection");
        assertEquals(0, ti.selectionStart.peekInt(), "selection start must survive");
        assertEquals(5, ti.selectionEnd.peekInt(), "selection end must survive");
        assertEquals(0, ti.selectionAnchor, "the selection anchor must survive");
        assertEquals(5, ti.cursorPosition.peekInt(), "the caret must survive");
        assertEquals(0, changes[0], "textChanged must not fire");
    }

    @Test
    void successfulCutConfirmsClipboardBeforeDeleting() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        RecordingClipboard cb = new RecordingClipboard();
        cb.editorText = ti.text::peek;
        v.setClipboard(cb);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.textChanged.connect(() -> cb.journal.add("textChanged@" + ti.text.peek()));

        assertTrue(v.cut(), "a confirmed cut must succeed");
        assertEquals(
            Arrays.asList("setText:hello@hello world", "getText@hello world", "textChanged@ world"),
            cb.journal,
            "the selection must be written and read back while the text is still intact");
        assertEquals(1, cb.setCalls, "exactly one write");
        assertEquals(1, cb.getCalls, "exactly one read back");
    }

    @Test
    void readOnlyNormalInputStillCopiesButDoesNotCut() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\"; readOnly: true }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);
        int[] changes = new int[1];
        ti.textChanged.connect(() -> changes[0]++);

        assertTrue(v.copy(), "a read-only Normal input must still copy");
        assertEquals("hello", cb.stored, "copy must publish the selection");
        assertEquals(1, cb.setCalls, "copy writes once");
        assertEquals(1, cb.getCalls, "copy reads back once");

        assertFalse(v.cut(), "a read-only input must not cut");
        assertEquals(1, cb.setCalls, "the refused cut must not write again");
        assertEquals(1, cb.getCalls, "the refused cut must not read again");
        assertEquals("hello world", ti.text.peek(), "the refused cut must keep the text");
        assertEquals(0, ti.selectionStart.peekInt(), "selection start must survive");
        assertEquals(5, ti.selectionEnd.peekInt(), "selection end must survive");
        assertEquals(0, ti.selectionAnchor, "the selection anchor must survive");
        assertEquals(5, ti.cursorPosition.peekInt(), "the caret must survive");
        assertEquals(0, changes[0], "textChanged must not fire");
    }

    // A backend that answers null to a read back has not refused the write -- it cannot
    // confirm it yet. Wayland does this: the clipboard string arrives with the compositor's
    // selection event, which is not dispatched while the key callback is still running.
    @Test
    void copySucceedsWhenTheBackendCannotConfirmTheWriteYet() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.ignoreWrites = true;
        cb.stored = null;
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);

        assertTrue(v.copy(), "an unconfirmable write must still count as a copy");
        assertEquals("hello world", ti.text.peek(), "a copy never edits the text");
    }

    @Test
    void cutIsRefusedWhenTheBackendCannotConfirmTheWrite() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        cb.ignoreWrites = true;
        cb.stored = null;
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(5);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(5);

        assertFalse(v.cut(), "an unconfirmable write must never authorise a cut");
        assertEquals("hello world", ti.text.peek(), "the text must survive");
    }

    // A binding can shorten the text while a selection still points past the new end.
    // Reachable from the desktop host, where a keyboard shortcut drives copy/cut.
    @Test
    void copySurvivesASelectionLeftPastTheEndOfTheText() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(11);
        ti.text.set("hi");

        assertTrue(v.copy(), "a stale selection must clamp, not throw");
        assertEquals("hi", cb.stored, "the copy is bounded by the current text");
    }

    @Test
    void cutSurvivesASelectionLeftPastTheEndOfTheText() {
        QmlView v = newView();
        RecordingClipboard cb = new RecordingClipboard();
        v.setClipboard(cb);
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(11);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(11);
        ti.text.set("hi");

        assertTrue(v.cut(), "a stale selection must clamp, not throw");
        assertEquals("", ti.text.peek(), "the clamped selection is what gets removed");
    }

    @Test
    void typingReplacesASelectionLeftPastTheEndOfTheText() {
        QmlView v = newView();
        Item root = v.load(
            "Item { width: 200; height: 100\n" +
            "  TextInput { focus: true; text: \"hello world\" }\n" +
            "}");
        TextInput ti = (TextInput) root.children.get(0);
        ti.selectionStart.set(0);
        ti.selectionEnd.set(11);
        ti.selectionAnchor = 0;
        ti.cursorPosition.set(11);
        ti.text.set("hi");

        v.dispatchKey(0, "X", true);

        assertEquals("X", ti.text.peek(), "the clamped selection is what gets replaced");
    }

    // Models the backends the postcondition exists for: ones that silently ignore
    // writes, rewrite what they were given, or throw in either direction. Every call
    // is journalled with the editor text visible at that moment, so a test can prove
    // the read back happened while the selection was still present.
    private static final class RecordingClipboard implements Clipboard {
        final List<String> journal = new ArrayList<>();
        String stored;
        boolean ignoreWrites;
        UnaryOperator<String> rewrite;
        RuntimeException setFailure;
        RuntimeException getFailure;
        Supplier<String> editorText;
        int setCalls;
        int getCalls;

        @Override public String getText() {
            getCalls++;
            journal.add("getText" + editorState());
            if (getFailure != null) throw getFailure;
            return stored;
        }

        @Override public void setText(String text) {
            setCalls++;
            journal.add("setText:" + text + editorState());
            if (setFailure != null) throw setFailure;
            if (!ignoreWrites) stored = rewrite == null ? text : rewrite.apply(text);
        }

        private String editorState() {
            return editorText == null ? "" : "@" + editorText.get();
        }
    }
}
