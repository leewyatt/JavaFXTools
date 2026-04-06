package io.github.leewyatt.fxtools.paintpicker;

import javax.swing.JTextField;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Locale;

/**
 * A text field that accepts only double values.
 */
public class DoubleField extends JTextField {

    public DoubleField() {
        this(6);
    }

    public DoubleField(int columns) {
        super(columns);
        PlainDocument doc = new PlainDocument();
        doc.setDocumentFilter(new DoubleDocumentFilter());
        setDocument(doc);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                selectAll();
            }
        });
    }

    @Override
    public void paste() {
        try {
            String text = (String) Toolkit.getDefaultToolkit()
                    .getSystemClipboard().getData(DataFlavor.stringFlavor);
            Double.parseDouble(text);
            super.paste();
        } catch (Exception e) {
            // invalid paste content, ignore
        }
    }

    private class DoubleDocumentFilter extends DocumentFilter {

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            string = string.replace(',', '.');
            String newText = getText(fb, offset, 0, string);
            if (isValidInput(newText)) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text != null) {
                text = text.replace(',', '.');
            }
            String newText = getText(fb, offset, length, text);
            if (text == null || text.isEmpty() || isValidInput(newText)) {
                super.replace(fb, offset, length, text, attrs);
            }
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            super.remove(fb, offset, length);
        }

        private String getText(FilterBypass fb, int offset, int length, String insertText)
                throws BadLocationException {
            String currentText = fb.getDocument().getText(0, fb.getDocument().getLength());
            String before = currentText.substring(0, offset);
            String after = currentText.substring(offset + length);
            return before + (insertText != null ? insertText : "") + after;
        }

        private boolean isValidInput(String text) {
            if (text.isEmpty() || text.equals("-") || text.equals(".") || text.equals("-.")) {
                return true;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (lower.contains("d") || lower.contains("f")) {
                return false;
            }
            try {
                Double.parseDouble(text);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
