package com.w6w.migrate.pdf;

import com.w6w.migrate.model.BlueprintIntermediateModel;
import com.w6w.migrate.model.BlueprintIntermediateModel.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Extracts the structured case, stage, step and data-model information from a Pega Blueprint PDF.
 * The adapter uses the document's layout and the step glyph colors; it does not depend on case names.
 */
public class PdfBlueprintExtractor {
    private static final Pattern BLUEPRINT_ID = Pattern.compile("Blueprint ID:\\s*(BP-[A-Za-z0-9-]+)", Pattern.CASE_INSENSITIVE);
    private static final List<String> DATA_TYPES = List.of(
            "User Reference", "DateTime", "Currency", "Integer", "Boolean", "Decimal",
            "Email", "Phone", "Text", "Date", "Data");

    public BlueprintIntermediateModel extract(File pdfFile) throws IOException {
        BlueprintIntermediateModel model = new BlueprintIntermediateModel();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            String allText = new PDFTextStripper().getText(document);
            Matcher id = BLUEPRINT_ID.matcher(allText);
            if (id.find()) model.setBlueprintId(id.group(1));

            PDFRenderer renderer = new PDFRenderer(document);
            List<TextLine> pageLines = new ArrayList<>();
            List<BufferedImage> pageImages = new ArrayList<>();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                PageStripper stripper = new PageStripper(page + 1);
                stripper.getText(document);
                List<TextLine> lines = toLines(stripper.glyphs, page);
                pageLines.addAll(lines);
                pageImages.add(renderer.renderImage(page, 2f));
                if (page == 0) parseOverview(lines, allText, model);
            }

            IntermediateCaseType activeCase = null;
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                List<TextLine> lines = toLinesForPage(pageLines, page);
                String title = findCaseTitle(lines);
                if (title != null) {
                    activeCase = new IntermediateCaseType();
                    activeCase.setName(title);
                    model.getCaseTypes().add(activeCase);
                    parseCasePage(lines, pageImages.get(page), activeCase);
                } else if (activeCase != null) {
                    parseContinuationPage(lines, activeCase);
                }
            }
        }
        return model;
    }

    private void parseOverview(List<TextLine> lines, String fullText, BlueprintIntermediateModel model) {
        String[] plainLines = fullText.split("\\R");
        for (int i = 0; i < plainLines.length - 1; i++) {
            if (plainLines[i].trim().equalsIgnoreCase("APPLICATION OVERVIEW DOCUMENT")) {
                model.setApplicationName(plainLines[i + 1].trim());
                break;
            }
        }
        int personaStart = -1, workflowStart = -1;
        for (int i = 0; i < plainLines.length; i++) {
            String line = plainLines[i].trim().toUpperCase(Locale.ROOT);
            if (line.contains("PERSONAS AND CHANNELS")) personaStart = i;
            else if (personaStart >= 0 && line.equals("WORKFLOWS")) { workflowStart = i; break; }
        }
        if (personaStart >= 0 && workflowStart > personaStart) {
            for (int i = personaStart + 1; i < workflowStart; i++) {
                String persona = plainLines[i].replaceAll("[^\\p{L}\\p{N} &'-]", "").trim();
                if (!persona.isBlank() && !persona.matches("\\d+")) model.getPersonas().add(persona);
            }
        }
    }

    private String findCaseTitle(List<TextLine> lines) {
        if (Double.isNaN(findY(lines, "CASE DATA MODEL", Double.NaN))) return null;
        for (TextLine line : lines) {
            if (line.y > 100 || line.fontSize < 14.0 || line.fontSize > 17.0) continue;
            List<Phrase> phrases = phrases(line, 24.0);
            for (Phrase phrase : phrases) {
                if (phrase.x < 145 && !phrase.text.isBlank()
                        && !phrase.text.toLowerCase(Locale.ROOT).contains("workflows")
                        && !phrase.text.equalsIgnoreCase("case types")) {
                    return phrase.text.trim().replaceFirst("(?i)\\s+Release\\s+\\d+$", "");
                }
            }
        }
        return null;
    }

    private void parseCasePage(List<TextLine> lines, BufferedImage pageImage, IntermediateCaseType caseType) {
        TextLine titleLine = lines.stream().filter(line -> line.y < 100 && line.fontSize >= 14.0 && line.fontSize <= 17.0)
                .findFirst().orElse(null);
        double titleY = titleLine == null ? 0 : titleLine.y;
        double dataModelY = findY(lines, "CASE DATA MODEL", Double.POSITIVE_INFINITY);
        double stagesLabelY = findY(lines, "STAGES", Double.NaN);
        double altStagesY = findY(lines, "ALTERNATE STAGES", Double.POSITIVE_INFINITY);
        double processStart = Double.isNaN(stagesLabelY) ? titleY + 26 : stagesLabelY + 5;

        List<TextLine> descriptionLines = lines.stream()
                .filter(line -> line.y > titleY && line.y < processStart && line.fontSize >= 12.0 && line.fontSize < 14.5
                        && !line.text.equalsIgnoreCase("Stages"))
                .toList();
        caseType.setDescription(joinLines(descriptionLines));

        List<StageAnchor> anchors = new ArrayList<>();
        for (TextLine line : lines) {
            if (line.y < processStart || line.y >= dataModelY || line.fontSize < 12.0 || line.fontSize >= 14.5) continue;
            String low = line.text.toLowerCase(Locale.ROOT);
            if (low.contains("alternate stages") || low.equals("stages") || low.contains("release")) continue;
            for (Phrase phrase : phrases(line, 28.0)) {
                if (!phrase.text.isBlank() && phrase.text.length() <= 64) {
                    IntermediateStage stage = new IntermediateStage();
                    stage.setName(phrase.text.trim());
                    stage.setAlternate(!Double.isNaN(altStagesY) && line.y > altStagesY);
                    anchors.add(new StageAnchor(stage, phrase.x, line.y));
                    caseType.getStages().add(stage);
                }
            }
        }

        List<TaskPhrase> taskPhrases = new ArrayList<>();
        for (TextLine line : lines) {
            if (line.y < processStart || line.y >= dataModelY || line.fontSize < 14.0 || line.fontSize > 17.0) continue;
            for (Phrase phrase : phrases(line, 28.0)) {
                if (!phrase.text.isBlank()) taskPhrases.add(new TaskPhrase(phrase.text, phrase.x, line.y, line.fontSize));
            }
        }
        taskPhrases.sort(Comparator.comparingDouble((TaskPhrase item) -> item.y).thenComparingDouble(item -> item.x));
        List<TaskPhrase> merged = new ArrayList<>();
        for (TaskPhrase current : taskPhrases) {
            TaskPhrase previous = null;
            for (int i = merged.size() - 1; i >= 0; i--) {
                TaskPhrase candidate = merged.get(i);
                if (Math.abs(current.x - candidate.x) < 3.0 && current.y - candidate.y < 16.0) {
                    previous = candidate;
                    break;
                }
            }
            if (previous != null) previous.text = previous.text + " " + current.text;
            else merged.add(current);
        }
        for (TaskPhrase phrase : merged) {
            StageAnchor anchor = nearestAnchor(anchors, phrase.x, phrase.y);
            if (anchor == null) continue;
            String normalized = normalize(phrase.text);
            if (normalized.isBlank()) continue;
            String stepType = classifyIcon(pageImage, phrase.x, phrase.y);
            String childKey = null;
            if (normalized.toLowerCase(Locale.ROOT).startsWith("create child case")) {
                stepType = "childWorkflow";
                int dash = normalized.indexOf('-');
                if (dash >= 0 && dash + 1 < normalized.length()) childKey = slug(normalized.substring(dash + 1));
            }
            IntermediateStep step = new IntermediateStep(normalized, stepType);
            step.setChildWorkflowKey(childKey);
            step.setConfidence("childWorkflow".equals(stepType) || !"unknown".equals(stepType) ? "medium" : "low");
            if ("unknown".equals(stepType)) {
                step.setManualReviewReason("PDF step glyph could not be classified; assign a task type before deployment.");
            }
            anchor.stage.getSteps().add(step);
        }

        parseDataFields(lines, dataModelY, caseType);
        parseBusinessRules(lines, caseType);
    }

    private void parseContinuationPage(List<TextLine> lines, IntermediateCaseType caseType) {
        double dataModelY = findY(lines, "CASE DATA MODEL", Double.NaN);
        if (!Double.isNaN(dataModelY)) parseDataFields(lines, dataModelY, caseType);
        parseBusinessRules(lines, caseType);
    }

    private void parseDataFields(List<TextLine> lines, double dataModelY, IntermediateCaseType caseType) {
        if (Double.isNaN(dataModelY)) return;
        for (TextLine line : lines) {
            // Table rows are deliberately identified by their column positions and
            // type cell. Blueprint exports vary font sizes across pages and themes.
            if (line.y <= dataModelY + 8) continue;
            String name = line.words.stream().filter(word -> word.x < 130).map(word -> word.text)
                    .collect(Collectors.joining(" ")).trim();
            if (name.isBlank() || name.equalsIgnoreCase("Field Name")) continue;
            String type = line.words.stream().filter(word -> word.x > 425).map(word -> word.text)
                    .collect(Collectors.joining(" ")).trim();
            String matchingType = DATA_TYPES.stream().filter(value -> type.toLowerCase(Locale.ROOT).startsWith(value.toLowerCase(Locale.ROOT)))
                    .findFirst().orElse(null);
            if (matchingType == null) continue;
            String description = line.words.stream().filter(word -> word.x >= 130 && word.x < 425)
                    .map(word -> word.text).collect(Collectors.joining(" ")).trim();
            IntermediateDataField field = new IntermediateDataField();
            field.setName(name.replaceAll("[�…]+$", "").trim());
            field.setDescription(description);
            field.setType(matchingType);
            field.setPrimary(line.words.stream().anyMatch(word -> word.x >= 535 && (word.text.contains("✓") || word.text.contains("√"))));
            caseType.getDataFields().add(field);
        }
    }

    private void parseBusinessRules(List<TextLine> lines, IntermediateCaseType caseType) {
        int start = exactIndexOf(lines, "BUSINESS RULES");
        if (start < 0) return;
        for (int i = start + 1; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            String text = line.text.trim();
            if (text.isBlank() || text.toLowerCase(Locale.ROOT).startsWith("there are no business rules")) continue;
            if (text.toLowerCase(Locale.ROOT).startsWith("by utilizing") || text.contains("7/") || text.contains("pega.com/blueprint")) break;
            if (text.equalsIgnoreCase("Name Description")) continue;
            if (line.words.size() > 1) {
                String name = line.words.stream().filter(word -> word.x < 130).map(word -> word.text).collect(Collectors.joining(" ")).trim();
                String description = line.words.stream().filter(word -> word.x >= 130).map(word -> word.text).collect(Collectors.joining(" ")).trim();
                if (!name.isBlank() && !name.equalsIgnoreCase("Name")) {
                    caseType.getBusinessRules().add(new IntermediateBusinessRule(normalize(name), normalize(description)));
                }
            }
        }
    }

    private String classifyIcon(BufferedImage image, double textX, double textY) {
        int x0 = (int) Math.round((textX - 15) * 2), x1 = (int) Math.round((textX - 2) * 2);
        int y0 = (int) Math.round((textY - 5) * 2), y1 = (int) Math.round((textY + 10) * 2);
        int[] counts = new int[5];
        for (int x = Math.max(0, x0); x <= Math.min(image.getWidth() - 1, x1); x++) {
            for (int y = Math.max(0, y0); y <= Math.min(image.getHeight() - 1, y1); y++) {
                float[] hsb = Color.RGBtoHSB((image.getRGB(x, y) >> 16) & 255, (image.getRGB(x, y) >> 8) & 255, image.getRGB(x, y) & 255, null);
                if (hsb[1] < .45f || hsb[2] < .45f) continue;
                float hue = hsb[0] * 360;
                if (hue >= 85 && hue <= 175) counts[0]++;
                else if (hue >= 190 && hue <= 245) counts[1]++;
                else if (hue >= 5 && hue <= 38) counts[2]++;
                else if (hue >= 39 && hue <= 75) counts[3]++;
                else if (hue >= 245 && hue <= 320) counts[4]++;
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[best]) best = i;
        if (counts[best] < 4) return "unknown";
        return switch (best) {
            case 0 -> "userTask";
            case 1 -> "approval";
            case 2 -> "decision";
            case 3 -> "serviceTask";
            case 4 -> "parallel";
            default -> "unknown";
        };
    }

    private StageAnchor nearestAnchor(List<StageAnchor> anchors, double x, double y) {
        return anchors.stream().filter(anchor -> Math.abs(anchor.y - y) < 220)
                .min(Comparator.comparingDouble(anchor -> Math.abs(anchor.x - x) + Math.abs(anchor.y - y))).orElse(null);
    }

    private static List<Phrase> phrases(TextLine line, double gap) {
        List<Phrase> result = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        double startX = 0, previousEnd = Double.NaN;
        for (Word word : line.words) {
            if (text.length() > 0 && word.x - previousEnd > gap) {
                result.add(new Phrase(text.toString().trim(), startX));
                text.setLength(0);
            }
            if (text.length() == 0) startX = word.x;
            if (text.length() > 0) text.append(' ');
            text.append(word.text);
            previousEnd = word.x + word.width;
        }
        if (text.length() > 0) result.add(new Phrase(text.toString().trim(), startX));
        return result;
    }

    private static List<TextLine> toLinesForPage(List<TextLine> all, int page) {
        return all.stream().filter(line -> line.page == page).toList();
    }

    private static List<TextLine> toLines(List<TextPosition> glyphs, int page) {
        List<TextPosition> sorted = glyphs.stream().filter(glyph -> glyph.getUnicode() != null && !glyph.getUnicode().isBlank())
                .sorted(Comparator.comparingDouble(TextPosition::getYDirAdj).thenComparingDouble(TextPosition::getXDirAdj)).toList();
        List<TextLine> lines = new ArrayList<>();
        List<TextPosition> current = new ArrayList<>();
        double baseline = Double.NaN;
        for (TextPosition glyph : sorted) {
            double y = glyph.getYDirAdj();
            // Text in table cells can use slightly different baselines from the
            // label and type columns. Group within a row while keeping rows apart.
            if (!current.isEmpty() && Math.abs(y - baseline) > 3.0) {
                lines.add(toLine(current, page)); current = new ArrayList<>();
            }
            if (current.isEmpty()) baseline = y;
            current.add(glyph);
        }
        if (!current.isEmpty()) lines.add(toLine(current, page));
        return lines;
    }

    private static TextLine toLine(List<TextPosition> glyphs, int page) {
        List<TextPosition> ordered = glyphs.stream().sorted(Comparator.comparingDouble(TextPosition::getXDirAdj)).toList();
        List<Word> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        double start = 0, end = 0, ySum = 0, sizeSum = 0;
        int count = 0;
        for (TextPosition glyph : ordered) {
            String unicode = glyph.getUnicode();
            if (unicode == null || unicode.isBlank()) {
                if (word.length() > 0) { words.add(new Word(word.toString(), start, end - start)); word.setLength(0); }
                continue;
            }
            double x = glyph.getXDirAdj(), width = glyph.getWidthDirAdj();
            if (word.length() > 0 && x - end > Math.max(.5, glyph.getFontSizeInPt() * .09)) {
                words.add(new Word(word.toString(), start, end - start)); word.setLength(0);
            }
            if (word.length() == 0) start = x;
            word.append(unicode); end = x + width;
            ySum += glyph.getYDirAdj(); sizeSum += glyph.getFontSizeInPt(); count++;
        }
        if (word.length() > 0) words.add(new Word(word.toString(), start, end - start));
        String text = words.stream().map(w -> w.text).collect(Collectors.joining(" ")).replaceAll("\\s+", " ").trim();
        return new TextLine(text, words, ySum / Math.max(1, count), sizeSum / Math.max(1, count), page);
    }

    private static int indexOf(List<TextLine> lines, String needle) {
        String lower = needle.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).text.toLowerCase(Locale.ROOT).contains(lower)) return i;
        return -1;
    }

    private static int exactIndexOf(List<TextLine> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).text.trim().equalsIgnoreCase(needle)) return i;
        return -1;
    }

    private static double findY(List<TextLine> lines, String needle, double fallback) {
        String lower = needle.toLowerCase(Locale.ROOT);
        return lines.stream().filter(line -> line.text.toLowerCase(Locale.ROOT).contains(lower))
                .mapToDouble(line -> line.y).findFirst().orElse(fallback);
    }

    private static String joinLines(List<TextLine> lines) {
        return lines.stream().map(line -> line.text.trim()).filter(text -> !text.isBlank()).collect(Collectors.joining(" ")).trim();
    }

    private static String normalize(String text) {
        return text.replaceAll("�+", "").replaceAll("\\s+", " ").replaceAll("[…]+$", "").trim();
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static class PageStripper extends PDFTextStripper {
        private final List<TextPosition> glyphs = new ArrayList<>();
        PageStripper(int page) throws IOException { setStartPage(page); setEndPage(page); setSortByPosition(true); }
        @Override protected void processTextPosition(TextPosition text) {
            glyphs.add(text);
            super.processTextPosition(text);
        }
    }
    private record Word(String text, double x, double width) { }
    private record Phrase(String text, double x) { }
    private record StageAnchor(IntermediateStage stage, double x, double y) { }
    private static class TaskPhrase {
        String text; final double x; final double y; final double fontSize;
        TaskPhrase(String text, double x, double y, double fontSize) { this.text = text; this.x = x; this.y = y; this.fontSize = fontSize; }
    }
    private record TextLine(String text, List<Word> words, double y, double fontSize, int page) { }
}
