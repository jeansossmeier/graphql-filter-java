package com.intuit.graphql.filter.client;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Function;

import static java.util.regex.Matcher.*;

public class SqlQueryValueNormalizer {
    private static final String SINGLE_QUOTE = "'";
    private static final String ESCAPED_SINGLE_QUOTE = "''";
    private static final String PARENTHESIS_OPEN = "(";
    private static final String PARENTHESIS_CLOSE = ")";
    private static final String COMMA = ",";
    private static final String RIGHT_SINGLE_QUOTATION_MARK = "’";
    private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("'([^']*)'");

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

        return isList(queryString) ? sanitizeList(queryString) : sanitizeExpression(queryString);
    }

    private String sanitizeExpression(String input) {
        final Matcher matcher = QUOTED_STRING_PATTERN.matcher(input);
        final StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            final String quotedContent = matcher.group(1);
            final String sanitizedContent = sanitizeQuoted(quotedContent);
            matcher.appendReplacement(result, quoteReplacement(SINGLE_QUOTE + sanitizedContent + SINGLE_QUOTE));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String sanitizeQuoted(String content) {
        final String handledQuotes = handleSingleQuotes(content)
                .replace(SINGLE_QUOTE, ESCAPED_SINGLE_QUOTE);
        return queryValueTransformer.apply(handledQuotes);
    }

    private static String handleSingleQuotes(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFC)
                .replace(RIGHT_SINGLE_QUOTATION_MARK, SINGLE_QUOTE);
    }

    private String sanitizeList(String listString) {
        final String withoutParentheses = listString.substring(1, listString.length() - 1);
        final String[] values = withoutParentheses.split(COMMA + "\\s*");

        final String sanitizedList = String.join(COMMA + " ", Arrays.stream(values)
                .map(this::sanitizeExpression)
                .toArray(String[]::new));

        return PARENTHESIS_OPEN + sanitizedList + PARENTHESIS_CLOSE;
    }

    private boolean isList(String input) {
        return input.startsWith(PARENTHESIS_OPEN) && input.endsWith(PARENTHESIS_CLOSE);
    }
}