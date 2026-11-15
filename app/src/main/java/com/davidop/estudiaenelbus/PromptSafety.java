package com.davidop.estudiaenelbus;

public final class PromptSafety {
    private PromptSafety() {}

    public static String escapeUntrustedText(String value) {
        if (value == null || value.isEmpty()) return value == null ? "" : value;
        return value.replace("<|", "< |").replace("</s>", "</ s>");
    }
}
