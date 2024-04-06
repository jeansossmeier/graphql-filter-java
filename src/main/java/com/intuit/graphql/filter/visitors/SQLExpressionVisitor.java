package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.CompoundExpression;
import com.intuit.graphql.filter.ast.Expression;
import com.intuit.graphql.filter.ast.ExpressionField;
import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;
import com.intuit.graphql.filter.ast.UnaryExpression;
import com.intuit.graphql.filter.client.DefaultFieldValueTransformer;
import com.intuit.graphql.filter.client.FieldValuePair;
import com.intuit.graphql.filter.client.FieldValueTransformer;
import com.intuit.graphql.filter.client.SqlQueryValueNormalizer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SQLExpressionVisitor implements ExpressionVisitor<String> {
    private static final String DOUBLE_QUOTE = "\"";
    private static final String ESCAPED_DOUBLE_QUOTE = "\\\\\\\"";
    private static final String DEFAULT_METADATA_PREFIX = "metadata@";
    private static final SqlQueryValueNormalizer DEFAULT_NORMALIZER = new SqlQueryValueNormalizer();
    private static final Map<Operator, String> DEFAULT_MAPPINGS = new HashMap<>();
    private static final DefaultFieldValueTransformer DEFAULT_FIELD_VALUE_TRANSFORMER = new DefaultFieldValueTransformer();

    static {
        // Logical operators
        DEFAULT_MAPPINGS.put(Operator.AND, "AND");
        DEFAULT_MAPPINGS.put(Operator.OR, "OR");
        DEFAULT_MAPPINGS.put(Operator.NOT, "NOT");

        // Relational string operators
        DEFAULT_MAPPINGS.put(Operator.EQUALS, "=");
        DEFAULT_MAPPINGS.put(Operator.CONTAINS, "LIKE");
        DEFAULT_MAPPINGS.put(Operator.STARTS, "LIKE");
        DEFAULT_MAPPINGS.put(Operator.ENDS, "LIKE");

        // Relational numeric operators
        DEFAULT_MAPPINGS.put(Operator.LT, "<");
        DEFAULT_MAPPINGS.put(Operator.GT, ">");
        DEFAULT_MAPPINGS.put(Operator.EQ, "=");
        DEFAULT_MAPPINGS.put(Operator.GTE, ">=");
        DEFAULT_MAPPINGS.put(Operator.LTE, "<=");

        // Common operators
        DEFAULT_MAPPINGS.put(Operator.IN, "IN");
        DEFAULT_MAPPINGS.put(Operator.BETWEEN, "BETWEEN");
    }


    private final Deque<Operator> operatorStack;
    private final Deque<ExpressionField> fieldStack;
    private final Map<String, String> fieldMap;
    private FieldValueTransformer fieldValueTransformer;
    private SQLExpressionValueVisitor expressionValueVisitor;
    private CustomExpressionResolver customExpressionResolver = (fieldName, operator) -> null;
    private Map<Operator, String> mappings;
    private Map<String, List<String>> metadataCollector;
    private SqlQueryValueNormalizer sqlQueryValueNormalizer;

    private boolean generateWherePrefix = true;
    private String metadataPrefix = DEFAULT_METADATA_PREFIX;

    public SQLExpressionVisitor(final Map<String, String> fieldMap) {
        this.operatorStack = new ArrayDeque<>();
        this.fieldStack = new ArrayDeque<>();
        this.mappings = new HashMap<>(DEFAULT_MAPPINGS);
        this.metadataCollector = new HashMap<>();
        this.fieldMap = fieldMap;
        this.expressionValueVisitor = SQLExpressionValueVisitor.DEFAULT;
        this.fieldValueTransformer = DEFAULT_FIELD_VALUE_TRANSFORMER;
        this.sqlQueryValueNormalizer = DEFAULT_NORMALIZER;
    }

    public SQLExpressionVisitor(
            final Map<String, String> fieldMap,
            final FieldValueTransformer fieldValueTransformer) {
        this(fieldMap);
        this.fieldValueTransformer = fieldValueTransformer;
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

        final String rightOperand = binaryExpression.getRightOperand().accept(this, "");
        final String[] filterValues = rightOperand.replaceAll("[()]", "").split(",");
        collectMetadata(leftOperand, filterValues);

        final String normalizedRightOperand = normalizeString(rightOperand);
        if (customExpressionResolver.contains(leftOperand, binaryExpression.getOperator())) {
            final String resolvedOperator = resolveOperator(binaryExpression.getOperator());
            return formatCustomBinaryExpression(
                    data, leftOperand, resolvedOperator, normalizedRightOperand, binaryExpression, filterValues);
        } else {
            return formatBinaryExpression(data, leftOperand, binaryExpression, normalizedRightOperand);
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

    private String prepareCustomExpression(
            final String fieldName,
            final String resolvedOperator,
            final String queryString,
            final BinaryExpression binaryExpression,
            final String[] filterValues) {

        final CustomFieldExpression customExpression = customExpressionResolver.resolve(
                fieldName, binaryExpression.getOperator());

        if (filterValues.length == 1) {
            return customExpression.generateExpression(
                    binaryExpression, fieldName, queryString, resolvedOperator);
        }

        final String enclosingLogicalOperator =
                customExpression.getEnclosingLogicalOperator().getValue();
        return Arrays.stream(filterValues)
                .map(filterValue -> customExpression.generateExpression(
                        binaryExpression, fieldName, normalizeString(filterValue), resolvedOperator))
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
        metadataCollector.put(metadataPrefix + metaDataType, filterValueList);
    }

    public String resolveOperator(Operator operator) {
        return mappings.getOrDefault(operator, "");
    }

    public void addMapping(Operator operator, String sql) {
        mappings.put(operator, sql);
    }

    public void removeMapping(Operator operator) {
        mappings.remove(operator);
    }

    public Map<Operator, String> getMappings() {
        return mappings;
    }

    private String normalizeString(final String input) {
        return sqlQueryValueNormalizer.handle(input);
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

    public void setMetadataCollector(Map<String, List<String>> metadataCollector) {
        this.metadataCollector = metadataCollector;
    }

    public Map<String, List<String>> getMetadataCollector() {
        return metadataCollector;
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
