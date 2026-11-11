package com.anshu.tokengate.cache;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Embeddings "same topic" pakadte hain, "ulta matlab" nahi. Ye guards wo galtiyan rokte hain.
// Rule: shak ho to MISS (galat MISS = thoda paisa, galat HIT = galat jawab).
public final class SemanticGuards {

    private static final Set<String> NEGATIONS = Set.of(
            "not", "no", "never", "nor", "without", "cannot",
            "dont", "doesnt", "didnt", "isnt", "arent", "wasnt", "werent",
            "cant", "wont", "shouldnt", "wouldnt", "couldnt");

    private static final List<String> NEGATING_PREFIXES = List.of("un", "in", "im", "il", "ir", "dis", "non");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private SemanticGuards() {}

    /** true = semantic HIT dena safe hai */
    public static boolean isSafeMatch(String a, String b) {
        return !hasNegationMismatch(a, b) && !hasNumberMismatch(a, b);
    }

    static boolean hasNegationMismatch(String a, String b) {
        List<String> wordsA = words(a);
        List<String> wordsB = words(b);
        if (countNegations(wordsA) % 2 != countNegations(wordsB) % 2) {
            return true;
        }
        return hasPrefixAntonym(wordsA, wordsB) || hasPrefixAntonym(wordsB, wordsA);
    }

    static boolean hasNumberMismatch(String a, String b) {
        Set<String> numsA = numbers(a);
        Set<String> numsB = numbers(b);
        return !numsA.isEmpty() && !numsB.isEmpty() && !numsA.equals(numsB);
    }

    private static List<String> words(String text) {
        String cleaned = PromptNormalizer.normalize(text).replace("'", "").replace("\u2019", "");
        return Arrays.stream(cleaned.split("[^a-z0-9]+")).filter(w -> !w.isEmpty()).toList();
    }

    private static long countNegations(List<String> words) {
        return words.stream().filter(NEGATIONS::contains).count();
    }

    private static boolean hasPrefixAntonym(List<String> from, List<String> other) {
        Set<String> otherSet = new HashSet<>(other);
        for (String word : from) {
            if (otherSet.contains(word)) {
                continue;
            }
            for (String prefix : NEGATING_PREFIXES) {
                if (word.startsWith(prefix)
                        && word.length() - prefix.length() >= 3
                        && otherSet.contains(word.substring(prefix.length()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> numbers(String text) {
        Set<String> result = new HashSet<>();
        Matcher m = NUMBER.matcher(text == null ? "" : text);
        while (m.find()) {
            result.add(m.group());
        }
        return result;
    }
}