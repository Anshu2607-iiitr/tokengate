package com.anshu.tokengate.cache;

public final class PromptNormalizer {

    private PromptNormalizer() {}

    public static String normalize(String text) {
        // TODO 1: null ya blank ho to "" return karo
        // TODO 2: lowercase karo (hint: toLowerCase(java.util.Locale.ROOT))
        // TODO 3: ek ya zyada whitespace ko ek space se badlo (hint: replaceAll with regex "\\s+")
        // TODO 4: trim karo
        // TODO 5: END ke ? . ! hatao, beech ke nahi (hint: regex jo string ke end pe match kare: "[?.!]+$")
        // TODO 6: phir se trim karo (kyunki "india ?" me "?" hatne ke baad space bachega)
        if (text == null || text.isBlank()) {
    return "";
}
String result = text.toLowerCase(java.util.Locale.ROOT);
// TODO 3, 4, 5, 6 yahan, har ek:  result = result.....;
    result = result.replaceAll("\\s+", " ");   // TODO 3: bahut saare spaces → ek space
    result = result.trim(); 
                        // TODO 4: aage-peeche ke spaces hatao
                                                 // TODO 6: phir se trim
    result = result.replaceAll("[?.!]+$", "");   // TODO 5: end ke ? . ! hatao
    result = result.trim();                      // TODO 6: phir se trim

return result;
    }
}