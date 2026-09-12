package com.dypiu.nba.security;

/**
 * Security utility for input and spreadsheet output sanitization.
 */
public final class SecuritySanitizer {

    private SecuritySanitizer() {}

    /**
     * Neutralizes spreadsheet formula injection (CSV/Excel DDE attacks).
     * If text begins with formula trigger characters ('=', '+', '-', '@', '\t', '\r'),
     * it prepends a single quote so spreadsheet software treats it strictly as plain text.
     *
     * @param text raw input string
     * @return sanitized string safe for spreadsheet exports
     */
    public static String sanitizeFormulaInjection(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char firstChar = text.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
            return "'" + text;
        }
        return text;
    }
}
