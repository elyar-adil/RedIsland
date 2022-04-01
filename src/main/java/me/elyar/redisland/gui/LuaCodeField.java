package me.elyar.redisland.gui;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
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
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LuaCodeField extends CodeArea {
    private static final String[] KEYWORDS = new String[]{
            "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"
    };
    private static final String PAREN_PATTERN = "\\(|\\)";
    private static final String BRACE_PATTERN = "\\{|\\}";
    private static final String BRACKET_PATTERN = "\\[|\\]";
    private static final String SEMICOLON_PATTERN = "\\;";
    private static final String COLON_PATTERN = "\\:";/**/
    private static final String STRING_PATTERN = "(?=\\s*)\"([^\"\\\\]|\\\\.)*\"|'([^\"\\\\]|\\\\.)*'";
    private static final String COMMENT_PATTERN = "--[^\n]*";
    private static final String NUMBER_PATTERN = "-?(([1-9][0-9]*)|(0))(?:\\.[0-9]+)?";

    private ObservableSet<Integer> breakPoints = FXCollections.observableSet();
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";

    public ObservableSet<Integer> getBreakPoints() {
        return breakPoints;
    }

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"

                    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
                    + "|(?<PAREN>" + PAREN_PATTERN + ")"
                    + "|(?<BRACE>" + BRACE_PATTERN + ")"
                    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
                    + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
                    + "|(?<COLON>" + COLON_PATTERN + ")"
                    + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
    );


    public LuaCodeField() {
        super();
        // recompute the syntax highlighting for all text, 500 ms after user stops editing area
        Subscription cleanupWhenNoLongerNeedIt = this
                // plain changes = ignore style changes that are emitted when syntax highlighting is reapplied
                // multi plain changes = save computation by not rerunning the code multiple times
                //   when making multiple changes (e.g. renaming a method at multiple parts in file)
                .multiPlainChanges()
                // do not emit an event until 500 ms have passed since the last emission of previous stream
                .successionEnds(Duration.ofMillis(500))
                // run the following code block when previous stream emits an event
                .subscribe(ignore -> setStyleSpans(0, computeHighlighting(getText())));
        // when no longer need syntax highlighting and wish to clean up memory leaks
        // run: `cleanupWhenNoLongerNeedIt.unsubscribe();`
        getVisibleParagraphs().addModificationObserver(new VisibleParagraphStyler<>(this, this::computeHighlighting));

        // auto-indent: insert previous line's indents on enter
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

        getStylesheets().add(GuiUtils.getResourcePath("css/lua.css"));
        IntFunction<Node> numberFactory = LineNumberFactory.get(this);
        BreakPointFactory arrowFactory = new BreakPointFactory();
        IntFunction<Node> graphicFactory = line -> {
            Node lineNumber = numberFactory.apply(line);
            Node circle = arrowFactory.apply(line, breakPoints);
            HBox hbox = new HBox(
                    lineNumber,
                    circle);
            hbox.setCursor(Cursor.HAND);
            hbox.setAlignment(Pos.CENTER_LEFT);
            hbox.setOnMouseClicked(l -> {
                if (breakPoints.contains(line)) {
                    breakPoints.remove(line);
                } else {
                    breakPoints.add(line);
                }
            });
            lineNumber.setStyle("-fx-background-color:#efefef;");
            hbox.setStyle("-fx-background-color:#efefef; -fx-padding: 0 5px;");
            return hbox;
        };
        setParagraphGraphicFactory(graphicFactory);

    }

    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder
                = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("NUMBER") != null ? "number" :
                            matcher.group("PAREN") != null ? "paren" :
                                    matcher.group("BRACE") != null ? "brace" :
                                            matcher.group("BRACKET") != null ? "bracket" :
                                                    matcher.group("SEMICOLON") != null ? "semicolon" :
                                                            matcher.group("STRING") != null ? "string" :
                                                                    matcher.group("COLON") != null ? "colon" :
                                                                            matcher.group("KEYWORD") != null ? "keyword" :
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

    private Integer prevLineNo = null;

    public void setHighlightLine(Integer lineNo) {
        if (prevLineNo != null) {
            setParagraphStyle(prevLineNo, Collections.emptyList());
            prevLineNo = null;
        }
        if (lineNo != null) {
            lineNo--;
            setParagraphStyle(lineNo, Collections.singletonList("resumed-line"));
            prevLineNo = lineNo;
        }
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


}
