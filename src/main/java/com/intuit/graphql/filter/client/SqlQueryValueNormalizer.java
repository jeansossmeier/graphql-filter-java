package com.intuit.graphql.filter.client;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SqlQueryValueNormalizer {
    private static final String SINGLE_QUOTE = "'";
    private static final String ESCAPED_SINGLE_QUOTE = "''";
    private static final String PARENTHESIS_OPEN = "(";
    private static final String PARENTHESIS_CLOSE = ")";
    private static final String COMMA = ",";
    private static final String RIGHT_SINGLE_QUOTATION_MARK = "’";

    private Function<String, String> queryValueTransformer;

    public SqlQueryValueNormalizer() {
        this.queryValueTransformer = query -> query;
    }

    public SqlQueryValueNormalizer(Function<String, String> queryValueTransformer) {
        this.queryValueTransformer = queryValueTransformer;
    }

    public String handle(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return queryString;
        }

        if (isList(queryString)) {
            return sanitizeList(queryString);
        }

        return sanitize(queryString);
    }

    private String sanitize(String input) {
        if (isQuoted(input)) {
            return sanitizeQuoted(input);
        } else {
            return handleSingleQuotes(input);
        }
    }

    private String sanitizeList(String sanitized) {
        final String edgeless = sanitized.substring(1, sanitized.length() - 1);
        final String sanitizedList =
                Arrays.stream(edgeless.split(COMMA))
                        .map(this::sanitize)
                        .collect(Collectors.joining(","));

        return PARENTHESIS_OPEN + sanitizedList + PARENTHESIS_CLOSE;
    }

    private String sanitizeQuoted(String sanitized) {
        final String unquoted =
                handleSingleQuotes(sanitized.substring(1, sanitized.length() - 1));

        return SINGLE_QUOTE + queryValueTransformer.apply(unquoted) + SINGLE_QUOTE;
    }

    private static String handleSingleQuotes(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFC)
                .replace(RIGHT_SINGLE_QUOTATION_MARK, SINGLE_QUOTE)
                .replace(SINGLE_QUOTE, ESCAPED_SINGLE_QUOTE);
    }

    private boolean isQuoted(String sanitized) {
        return sanitized.startsWith(SINGLE_QUOTE) && sanitized.endsWith(SINGLE_QUOTE);
    }

    private boolean isList(String sanitized) {
        return sanitized.startsWith(PARENTHESIS_OPEN) && sanitized.endsWith(PARENTHESIS_CLOSE);
    }
}
