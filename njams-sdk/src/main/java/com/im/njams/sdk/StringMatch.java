package com.im.njams.sdk;

import com.im.njams.sdk.logmessage.DataMaskingType;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;

class StringMatch {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(StringMatch.class);

    private static final String MASK_CHAR = "*";

    private final String original;
    private boolean hasBeenChanged;
    private String masked;

    StringMatch(String stringToMatch) {
        this.original = stringToMatch;
        this.hasBeenChanged = false;
        this.masked = stringToMatch;
    }

    void tryToMatchWith(DataMaskingType dataMaskingType) {
        Matcher m = dataMaskingType.getPattern().matcher(original);
        while (m.find()) {
            int startIdx = m.start();
            int endIdx = m.end();

            String patternMatch = original.substring(startIdx, endIdx);
            String partToBeMasked = patternMatch;
            String mask = "";
            for (int i = 0; i < partToBeMasked.length(); i++) {
                mask = mask + MASK_CHAR;
            }

            String maskedNumber = mask + patternMatch.substring(patternMatch.length());
            masked = masked.replace(patternMatch, maskedNumber);
            hasBeenChanged = true;
        }
        logPatternIfUsed(dataMaskingType);
    }

    private void logPatternIfUsed(DataMaskingType dataMaskingType) {
        if (hasBeenChanged) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("\nApplied masking of pattern: \"{}\". \nThe regex is: \"{}\"",
                    dataMaskingType.getNameOfPattern(), dataMaskingType.getRegex());
            }
        }
    }

    boolean hasMatchedAtLeastOnce() {
        return hasBeenChanged;
    }

    String getMaskedString() {
        return masked;
    }
}
