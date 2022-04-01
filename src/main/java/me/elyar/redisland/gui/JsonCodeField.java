package me.elyar.redisland.gui;

import com.google.gson.*;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import me.elyar.redisland.StringUtils;
import me.elyar.redisland.gui.util.GuiUtils;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.reactfx.Subscription;
import org.reactfx.collection.ListModification;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonCodeField extends CodeArea {
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String COLON_PATTERN = "\\:";
    private static final String STRING_PATTERN = "(?=\\s*)\"([^\"\\\\]|\\\\.)*\"|'([^\"\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "//[^\n]*" + "|" + "(?s)/\\\\*.*?\\\\*/";
    private static final String NUMBER_PATTERN = "-?(([1-9][0-9]*)|(0))(?:\\.[0-9]+)?";

    private static final Pattern PATTERN = Pattern.compile(
            "(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<KEY>" + "(?=[\\,\\{\\s^])[^\\,\\{]*(?=\\s*\\:)" + ")"
                    + "|(?<COLON>" + COLON_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );

    public void showLineNumber(boolean on) {
        if (on) {
            setParagraphGraphicFactory(LineNumberFactory.get(this));
        } else {
            setParagraphGraphicFactory(null);
        }
    }

    private boolean highlight = true;

    public void setHighlight(boolean highlight) {
        this.highlight = highlight;
        setStyleSpans(0, computeHighlighting(getText()));
    }

    public JsonCodeField() {
        super();
        Subscription cleanupWhenNoLongerNeedIt = this
                .multiPlainChanges()
                .successionEnds(Duration.ofMillis(500))
                .subscribe(ignore -> setStyleSpans(0, computeHighlighting(getText())));
        getVisibleParagraphs().addModificationObserver
                (
                        new VisibleParagraphStyler<>(this, this::computeHighlighting)
                );

        final Pattern whiteSpace = Pattern.compile("^\\s+");
        addEventHandler(KeyEvent.KEY_PRESSED, KE ->
        {
            if (KE.getCode() == KeyCode.ENTER) {
                int caretPosition = getCaretPosition();
                int currentParagraph = getCurrentParagraph();
                Matcher m0 = whiteSpace.matcher(getParagraph(currentParagraph - 1).getSegments().get(0));
                if (m0.find()) Platform.runLater(() -> insertText(caretPosition, m0.group()));
            }
        });
        getStylesheets().add(GuiUtils.getResourcePath("css/json.css"));

    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
                = new StyleSpansBuilder<>();

        while (highlight && matcher.find()) {
            String styleClass =
                    matcher.group("NUMBER") != null ? "number" :
                            matcher.group("PAREN") != null ? "paren" :
                                    matcher.group("BRACE") != null ? "brace" :
                                            matcher.group("BRACKET") != null ? "bracket" :
                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                            matcher.group("STRING") != null ? "string" :
                                                                    matcher.group("COLON") != null ? "colon" :
                                                                            matcher.group("KEY") != null ? "key" :
                                                                                    matcher.group("COMMENT") != null ? "comment" :
                                                                                            null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }

    private static class VisibleParagraphStyler<PS, SEG, S> implements Consumer<ListModification<? extends Paragraph<PS, SEG, S>>> {
        private final GenericStyledArea<PS, SEG, S> area;
        private final Function<String, StyleSpans<S>> computeStyles;
        private int prevParagraph, prevTextLength;

        public VisibleParagraphStyler(GenericStyledArea<PS, SEG, S> area, Function<String, StyleSpans<S>> computeStyles) {
            this.computeStyles = computeStyles;
            this.area = area;
        }

        @Override
        public void accept(ListModification<? extends Paragraph<PS, SEG, S>> lm) {
            if (lm.getAddedSize() > 0) {
                int paragraph = Math.min(area.firstVisibleParToAllParIndex() + lm.getFrom(), area.getParagraphs().size() - 1);
                String text = area.getText(paragraph, 0, paragraph, area.getParagraphLength(paragraph));

                if (paragraph != prevParagraph || text.length() != prevTextLength) {
                    int startPos = area.getAbsolutePosition(paragraph, 0);
                    Platform.runLater(() -> area.setStyleSpans(startPos, computeStyles.apply(text)));
                    prevTextLength = text.length();
                    prevParagraph = paragraph;
                }
            }
        }
    }


    private String toPrettyFormat(String json) {
        if (StringUtils.isEmpty(json)) {
            return json;
        }
        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement je = jsonParser.parse(json);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            return gson.toJson(je);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return json;
        }
    }

    private String minify(String json) {
        if (StringUtils.isEmpty(json)) {
            return json;
        }
        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement je = jsonParser.parse(json);

            Gson gson = new GsonBuilder().create();
            return gson.toJson(je);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return json;
        }
    }

    public void format() {
        String json = getText();
        json = toPrettyFormat(json);
        replaceText(json);
    }

    public void minify() {
        String json = getText();
        json = minify(json);
        replaceText(json);
    }
}
