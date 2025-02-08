import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.TextPosition;

public class TitleExtractorByFontSize {
    private final List<TextPosition> textPositionsList = new ArrayList<>();

    public Optional<String> getTitle(PDDocument document) throws IOException {
//        this.setStartPage(1);
//        this.setEndPage(2);
//        this.writeText(document, new StringWriter());
        return findLargestFontText(textPositionsList);
    }

    protected void writeString(String text, List<TextPosition> textPositions) {
        textPositionsList.addAll(textPositions);
    }

    private boolean isFarAway(TextPosition previous, TextPosition current) {
        float XspaceThreshold = previous.getFontSizeInPt() * 3.0F;
        float YspaceThreshold = previous.getFontSizeInPt() * 3.0F;
        float Xgap = current.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
        float Ygap = current.getYDirAdj() - previous.getYDirAdj();
        // For cases like paper titles spanning two or more lines, both X and Y gaps must exceed thresholds,
        // so "&&" is used instead of "||".
        return Math.abs(Xgap) > XspaceThreshold && Math.abs(Ygap) > YspaceThreshold;
    }

    private boolean isUnwantedText(TextPosition previousTextPosition, TextPosition textPosition,
                                   Map<Float, TextPosition> lastPositionMap, float fontSize) {
        // This indicates that the text is at the start of the line, so it is needed.
        if (textPosition == null || previousTextPosition == null) {
            return false;
        }
        // We use the font size to identify titles. Blank characters don't have a font size, so we discard them.
        // The space will be added back in the final result, but not in this method.
        if (StringUtils.isBlank(textPosition.getUnicode())) {
            return true;
        }
        // Titles are generally not located in the bottom 10% of a page.
        if ((textPosition.getPageHeight() - textPosition.getYDirAdj()) < (textPosition.getPageHeight() * 0.1)) {
            return true;
        }
        // Characters in a title typically remain close together,
        // so a distant character is unlikely to be part of the title.
        return lastPositionMap.containsKey(fontSize) && isFarAway(lastPositionMap.get(fontSize), textPosition);
    }

    private Optional<String> findLargestFontText(List<TextPosition> textPositions) {
        Map<Float, StringBuilder> fontSizeTextMap = new TreeMap<>(Collections.reverseOrder());
        Map<Float, TextPosition> lastPositionMap = new TreeMap<>(Collections.reverseOrder());
        TextPosition previousTextPosition = null;
        for (TextPosition textPosition : textPositions) {
            float fontSize = textPosition.getFontSizeInPt();
            // Exclude unwanted text based on heuristics
            if (isUnwantedText(previousTextPosition, textPosition, lastPositionMap, fontSize)) {
                continue;
            }
            fontSizeTextMap.putIfAbsent(fontSize, new StringBuilder());
            if (previousTextPosition != null && isThereSpace(previousTextPosition, textPosition)) {
                fontSizeTextMap.get(fontSize).append(" ");
            }
            fontSizeTextMap.get(fontSize).append(textPosition.getUnicode());
            lastPositionMap.put(fontSize, textPosition);
            previousTextPosition = textPosition;
        }
        for (Map.Entry<Float, StringBuilder> entry : fontSizeTextMap.entrySet()) {
            String candidateText = entry.getValue().toString().trim();
            if (isLegalTitle(candidateText)) {
                return Optional.of(candidateText);
            }
        }
        return fontSizeTextMap.values().stream().findFirst().map(StringBuilder::toString).map(String::trim);
    }

    private boolean isLegalTitle(String candidateText) {
        // The minimum title length typically observed in academic research is 4 characters.
        return candidateText.length() >= 4;
    }

    private boolean isThereSpace(TextPosition previous, TextPosition current) {
        float XSpaceThreshold = 1F;
        float YSpaceThreshold = previous.getFontSizeInPt();
        float XGap = current.getXDirAdj() - (previous.getXDirAdj() + previous.getWidthDirAdj());
        float YGap = current.getYDirAdj() - (previous.getYDirAdj() - previous.getHeightDir());
        return Math.abs(XGap) > XSpaceThreshold || Math.abs(YGap) > YSpaceThreshold;
    }
}


