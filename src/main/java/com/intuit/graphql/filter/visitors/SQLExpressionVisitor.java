package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.CompoundExpression;
import com.intuit.graphql.filter.ast.Expression;
import com.intuit.graphql.filter.ast.ExpressionField;
import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;
import com.intuit.graphql.filter.ast.UnaryExpression;
import com.intuit.graphql.filter.client.FieldValuePair;
import com.intuit.graphql.filter.client.FieldValueTransformer;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SQLExpressionVisitor implements ExpressionVisitor<String> {
    private static final char RIGHT_SINGLE_QUOTATION_MARK = '’';
    private static final char APOSTROPHE = '\'';
    private static final String DOUBLE_QUOTE = "\"";
    private static final String ESCAPED_DOUBLE_QUOTE = "\\\\\\\"";
    private static final String DEFAULT_METADATA_PREFIX = "params@";

    private static final Map<Operator, String> MAPPINGS = new HashMap<>();

    private boolean generateWherePrefix = true;
    private String metadataPrefix = DEFAULT_METADATA_PREFIX;

    private final Deque<Operator> operatorStack;
    private final Deque<ExpressionField> fieldStack;
    private final Map<String, String> fieldMap;
    private FieldValueTransformer fieldValueTransformer;
    private SQLExpressionValueVisitor expressionValueVisitor;
    private CustomExpressionResolver customExpressionResolver = (fieldName, operator) -> null;
    private Map<String, List<String>> metadata;

    static {
        // Logical operators
        MAPPINGS.put(Operator.AND, "AND");
        MAPPINGS.put(Operator.OR, "OR");
        MAPPINGS.put(Operator.NOT, "NOT");

        // Relational string operators
        MAPPINGS.put(Operator.EQUALS, "=");
        MAPPINGS.put(Operator.CONTAINS, "LIKE");
        MAPPINGS.put(Operator.STARTS, "LIKE");
        MAPPINGS.put(Operator.ENDS, "LIKE");

        // Relational numeric operators
        MAPPINGS.put(Operator.LT, "<");
        MAPPINGS.put(Operator.GT, ">");
        MAPPINGS.put(Operator.EQ, "=");
        MAPPINGS.put(Operator.GTE, ">=");
        MAPPINGS.put(Operator.LTE, "<=");

        // Common operators
        MAPPINGS.put(Operator.IN, "IN");
        MAPPINGS.put(Operator.BETWEEN, "BETWEEN");
    }

    public static String resolveOperator(Operator operator) {
        return MAPPINGS.getOrDefault(operator, "");
    }

    public static void addMapping(Operator operator, String sql) {
        MAPPINGS.put(operator, sql);
    }

    public static void removeMapping(Operator operator) {
        MAPPINGS.remove(operator);
    }

    public SQLExpressionVisitor(
            final Map<String, String> fieldMap,
            final FieldValueTransformer fieldValueTransformer) {
        this.operatorStack = new ArrayDeque<>();
        this.fieldStack = new ArrayDeque<>();
        this.metadata = new HashMap<>();
        this.fieldMap = fieldMap;
        this.fieldValueTransformer = fieldValueTransformer;
        this.expressionValueVisitor = SQLExpressionValueVisitor.DEFAULT;
    }

    /** Returns the SQL WHERE clause string from the expression tree. */
    @Override
    public String expression(final Expression expression) {
        String expressionString = generateWherePrefix ? "WHERE " : "";
        if (expression != null) {
            expressionString = expression.accept(this, expressionString);
        }

        return expressionString;
    }

    /**
     * Handles the processing of compound expression node.
     *
     * @param compoundExpression Contains compound expression.
     * @param data Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public String visitCompoundExpression(
            final CompoundExpression compoundExpression, final String data) {
        return new StringBuilder(data)
                .append("(")
                .append(compoundExpression.getLeftOperand().accept(this, ""))
                .append(" ")
                .append(resolveOperator(compoundExpression.getOperator()).toUpperCase())
                .append(" ")
                .append(compoundExpression.getRightOperand().accept(this, ""))
                .append(")")
                .toString();
    }

    /**
     * Handles the processing of binary expression node.
     *
     * @param binaryExpression Contains binary expression.
     * @param data Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public String visitBinaryExpression(final BinaryExpression binaryExpression, final String data) {
        final String leftOperand = binaryExpression.getLeftOperand().accept(this, "");
        operatorStack.push(binaryExpression.getOperator());

        final String rightOperand =
                normalizeString(binaryExpression.getRightOperand().accept(this, ""));

        final String[] filterValues = rightOperand.replaceAll("[()]", "").split(",");
        collectMetadata(leftOperand, filterValues);

        final String resolvedOperator = resolveOperator(binaryExpression.getOperator());
        if (customExpressionResolver.contains(leftOperand, resolvedOperator)) {
            return formatCustomBinaryExpression(
                    data, leftOperand, resolvedOperator, rightOperand, binaryExpression, filterValues);
        } else {
            return formatBinaryExpression(data, leftOperand, binaryExpression, rightOperand);
        }
    }

    /**
     * Handles the processing of unary expression node.
     *
     * @param unaryExpression Contains unary expression.
     * @param data Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public String visitUnaryExpression(final UnaryExpression unaryExpression, final String data) {
        return new StringBuilder(data)
                .append("( ")
                .append(resolveOperator(unaryExpression.getOperator()))
                .append(" ")
                .append(unaryExpression.getLeftOperand().accept(this, ""))
                .append(")")
                .toString();
    }

    /**
     * Handles the processing of expression field node.
     *
     * @param field Contains expression field.
     * @param data Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public String visitExpressionField(final ExpressionField field, final String data) {
        final StringBuilder expressionBuilder = new StringBuilder(data);
        if (fieldMap != null && fieldMap.get(field.infix()) != null) {
            expressionBuilder.append(fieldMap.get(field.infix()));
        } else if (fieldValueTransformer != null && fieldValueTransformer.transformField(field.infix()) != null) {
            expressionBuilder.append(fieldValueTransformer.transformField(field.infix()));
            fieldStack.push(field); //pushing the field for lookup while visiting value.
        } else {
            expressionBuilder.append(field.infix());
        }
        return expressionBuilder.toString();
    }

    /**
     * Handles the processing of expression value node.
     *
     * @param expressionValue Contains expression value.
     * @param data Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public String visitExpressionValue(
            final ExpressionValue<? extends Comparable> expressionValue, final String data) {

        final Operator operator = operatorStack.pop();
        ExpressionValue<? extends Comparable> value = expressionValue;
        if (!fieldStack.isEmpty() && fieldValueTransformer != null) {
            ExpressionField field  = fieldStack.pop(); // pop the field associated with this value.
            FieldValuePair fieldValuePair = fieldValueTransformer.transformValue(field.infix(),value.value());
            if (fieldValuePair != null && fieldValuePair.getValue() != null) {
                value = new ExpressionValue(fieldValuePair.getValue());
            }
        }

        return expressionValueVisitor.visitExpressionValue(operator, value, data);
    }

    private CustomFieldExpression resolveCustomExpressions(
            final String fieldName, final String resolvedOperator) {
        return customExpressionResolver.resolve(fieldName, resolvedOperator);
    }

    private String prepareCustomExpression(
            final String fieldName,
            final String resolvedOperator,
            final String queryString,
            final BinaryExpression binaryExpression,
            final String[] filterValues) {

        final CustomFieldExpression customExpression =
                resolveCustomExpressions(fieldName, resolvedOperator);

        if (filterValues.length == 1) {
            return customExpression.generateExpression(
                    binaryExpression, fieldName, queryString, resolvedOperator);
        }

        final String enclosingLogicalOperator =
                customExpression.getEnclosingLogicalOperator().getValue();
        return Arrays.stream(filterValues)
                .map(filterValue -> customExpression.generateExpression(
                        binaryExpression, fieldName, filterValue, resolvedOperator))
                .collect(Collectors.joining(" " + enclosingLogicalOperator + " "));
    }

    private String formatBinaryExpression(
            String data,
            String leftOperand,
            BinaryExpression binaryExpression,
            String rightOperand) {

        final String resolvedOperator = resolveOperator(binaryExpression.getOperator());
        return String.format("%s(%s %s %s)", data, leftOperand, resolvedOperator, rightOperand);
    }

    private String formatCustomBinaryExpression(
            String data,
            String leftOperand,
            String operator,
            String rightOperand,
            BinaryExpression binaryExpression,
            String[] filterValues) {

        final String customExpression = prepareCustomExpression(
                leftOperand, operator, rightOperand, binaryExpression, filterValues);

        return String.format("(%s %s)", data, customExpression);
    }

    private String normalizeString(final String target) {
        return Normalizer.normalize(target, Normalizer.Form.NFC)
                .replace(RIGHT_SINGLE_QUOTATION_MARK, APOSTROPHE);
    }

    private void collectMetadata(final String metaDataType, final String[] filterValues) {
        final List<String> filterValueList = new ArrayList<>();
        for (String filterValue : filterValues) {
            final String normalizedFilterValue =
                    filterValue.replace(DOUBLE_QUOTE, ESCAPED_DOUBLE_QUOTE);
            filterValueList.add(
                    new StringBuilder()
                            .append(DOUBLE_QUOTE)
                            .append(normalizedFilterValue, 1, normalizedFilterValue.length() - 1)
                            .append(DOUBLE_QUOTE)
                            .toString());
        }
        metadata.put(metadataPrefix + metaDataType, filterValueList);
    }

    public boolean isGenerateWherePrefix() {
        return generateWherePrefix;
    }

    public void setGenerateWherePrefix(boolean generateWherePrefix) {
        this.generateWherePrefix = generateWherePrefix;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public void setMetadataPrefix(String metadataPrefix) {
        this.metadataPrefix = metadataPrefix;
    }

    public Map<String, List<String>> getMetadata() {
        return metadata;
    }

    public SQLExpressionValueVisitor getExpressionValueVisitor() {
        return expressionValueVisitor;
    }

    public void setExpressionValueVisitor(SQLExpressionValueVisitor expressionValueVisitor) {
        this.expressionValueVisitor = expressionValueVisitor;
    }

    public CustomExpressionResolver getCustomExpressionResolver() {
        return customExpressionResolver;
    }

    public void setCustomExpressionResolver(CustomExpressionResolver customExpressionResolver) {
        this.customExpressionResolver = customExpressionResolver;
    }
}
