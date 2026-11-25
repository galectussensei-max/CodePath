package com.codepath.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last line of defence behind the prompts: the model is instructed not to leak solutions, and this class
 * checks that it complied. Levels 1-3 and reviews must contain no code; Level 4 must be one skeleton with
 * blanks. Nothing that fails these checks is stored, counted or returned.
 */
public final class AiResponseGuard {

    public static final int MAX_REPLY_CHARS = 4000;
    static final String BLANK = "____";
    static final int MIN_BLANKS = 3;
    static final int MAX_CODE_LINES = 60;

    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*\\R(.*?)```", Pattern.DOTALL);
    private static final List<String> JAVA_MARKERS = List.of(
            "public static void main", "class Main", "import java.", "System.out.", "new Scanner(",
            "BufferedReader", "public class ", "return new int[");

    private AiResponseGuard() {
    }

    /** @return null when the reply is acceptable, otherwise a short reason (also used in the retry prompt) */
    public static String violation(InteractionType type, String reply) {
        if (reply == null || reply.isBlank()) {
            return "the reply was empty";
        }
        if (reply.length() > MAX_REPLY_CHARS) {
            return "the reply was too long";
        }
        if (type == InteractionType.HINT_L4) {
            return clozeViolation(reply);
        }
        return noCodeViolation(reply);
    }

    private static String noCodeViolation(String reply) {
        if (reply.contains("```")) {
            return "it contained a code block, which is not allowed at this level";
        }
        for (String marker : JAVA_MARKERS) {
            if (reply.contains(marker)) {
                return "it contained Java code (" + marker.strip() + "), which is not allowed at this level";
            }
        }
        return null;
    }

    private static String clozeViolation(String reply) {
        Matcher matcher = FENCE.matcher(reply);
        int blocks = 0;
        String code = "";
        while (matcher.find()) {
            blocks++;
            code = matcher.group(1);
        }
        if (blocks != 1) {
            return "it must contain exactly one fenced Java code block";
        }
        int blanks = countOccurrences(code, BLANK);
        if (blanks < MIN_BLANKS) {
            return "the skeleton must contain at least " + MIN_BLANKS + " blanks written as ____";
        }
        long lines = code.lines().filter(line -> !line.isBlank()).count();
        if (lines > MAX_CODE_LINES) {
            return "the skeleton was too long";
        }
        long blankLines = code.lines().filter(line -> line.contains(BLANK)).count();
        long logicLines = code.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.equals("{") && !line.equals("}") && !line.startsWith("//")
                        && !line.startsWith("import ") && !line.startsWith("*") && !line.startsWith("/*"))
                .count();
        if (logicLines > 0 && blankLines * 5 < logicLines) {
            return "too little of the skeleton was left blank, so it looks like a full solution";
        }
        return null;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
