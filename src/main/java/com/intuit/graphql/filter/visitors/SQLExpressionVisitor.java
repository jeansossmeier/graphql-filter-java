/*
  Copyright 2020 Intuit Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.CompoundExpression;
import com.intuit.graphql.filter.ast.UnaryExpression;
import com.intuit.graphql.filter.ast.Expression;
import com.intuit.graphql.filter.ast.ExpressionField;
import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;
import com.intuit.graphql.filter.client.FieldValuePair;
import com.intuit.graphql.filter.client.FieldValueTransformer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is responsible for traversing
 * the expression tree and generating an
 * SQL WHERE clause from it with correct precedence
 * order marked by parenthesis.
 *
 * @author sjaiswal
 * @author jeansossmeier
 */
public class SQLExpressionVisitor implements ExpressionVisitor<String> {
    private static final SQLExpressionValueVisitor DEFAULT_SQL_EXPRESSION_VALUE_VISITOR = new SQLExpressionValueVisitor();
    private static final Map<Operator, String> MAPPINGS = new HashMap<>();

    private Deque<Operator> operatorStack;
    private Map<String, String> fieldMap;
    private Deque<ExpressionField> fieldStack;
    private FieldValueTransformer fieldValueTransformer;

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
            Map<String,String> fieldMap,
            FieldValueTransformer fieldValueTransformer
    ) {
        this.operatorStack = new ArrayDeque<>();
        this.fieldMap = fieldMap;
        this.fieldStack = new ArrayDeque<>();
        this.fieldValueTransformer = fieldValueTransformer;
    }

    /**
     * Returns the SQL WHERE clause string
     * from the expression tree.
     * @return
     * @param expression
     */
    @Override
    public String expression(Expression expression) {
        String expressionString = "WHERE ";
        if (expression != null) {
            expressionString = expression.accept(this, expressionString);
        }
        return expressionString;
    }

    /**
     * Handles the processing of compound
     * expression node.
     * @param compoundExpression
     *          Contains compound expression.
     * @param data
     *          Buffer for storing processed data.
     * @return
     *          Data of processed node.
     */
    @Override
    public String visitCompoundExpression(CompoundExpression compoundExpression, String data) {
        StringBuilder expressionBuilder = new StringBuilder(data);
        expressionBuilder.append("(")
                .append(compoundExpression.getLeftOperand().accept(this, ""))
                .append(" ").append(compoundExpression.getOperator().getName().toUpperCase()).append(" ")
                .append(compoundExpression.getRightOperand().accept(this, ""))
                .append(")");
        return expressionBuilder.toString();

    }

    /**
     * Handles the processing of binary
     * expression node.
     * @param binaryExpression
     *          Contains binary expression.
     * @param data
     *          Buffer for storing processed data.
     * @return
     *          Data of processed node.
     */
    @Override
    public String visitBinaryExpression(BinaryExpression binaryExpression, String data) {
        StringBuilder expressionBuilder = new StringBuilder(data);
        expressionBuilder.append("(")
                .append(binaryExpression.getLeftOperand().accept(this, ""))
                .append(" ").append(resolveOperator(binaryExpression.getOperator())).append(" ");
        operatorStack.push(binaryExpression.getOperator());
        expressionBuilder.append(binaryExpression.getRightOperand().accept(this, ""))
                .append(")");
        return expressionBuilder.toString();
    }

    /**
     * Handles the processing of unary
     * expression node.
     * @param unaryExpression
     *          Contains unary expression.
     * @param data
     *          Buffer for storing processed data.
     * @return
     *          Data of processed node.
     */
    @Override
    public String visitUnaryExpression(UnaryExpression unaryExpression, String data) {
        StringBuilder expressionBuilder = new StringBuilder(data);
        expressionBuilder.append("(")
                .append(" ").append(resolveOperator(unaryExpression.getOperator())).append(" ")
                .append(unaryExpression.getLeftOperand().accept(this, ""))
                .append(")");
        return expressionBuilder.toString();
    }

    /**
     * Handles the processing of expression
     * field node.
     * @param field
     *          Contains expression field.
     * @param data
     *          Buffer for storing processed data.
     * @return
     *          Data of processed node.
     */
    @Override
    public String visitExpressionField(ExpressionField field, String data) {
        StringBuilder expressionBuilder = new StringBuilder(data);
        if (fieldMap != null && fieldMap.get(field.stringValue()) != null) {
            expressionBuilder.append(fieldMap.get(field.stringValue()));
        } else if (fieldValueTransformer != null && fieldValueTransformer.transformField(field.stringValue()) != null) {
            expressionBuilder.append(fieldValueTransformer.transformField(field.stringValue()));
            fieldStack.push(field); //pushing the field for lookup while visiting value.
        } else {
            expressionBuilder.append(field.stringValue());
        }
        return expressionBuilder.toString();
    }

    /**
     * Handles the processing of expression
     * value node.
     * @param value
     *          Contains expression value.
     * @param data
     *          Buffer for storing processed data.
     * @return
     *          Data of processed node.
     */
    @Override
    public String visitExpressionValue(ExpressionValue<? extends Comparable> expressionValue, String data) {
        final Operator operator = operatorStack.pop();

        ExpressionValue<? extends Comparable> value = expressionValue;
        if (!fieldStack.isEmpty() && fieldValueTransformer != null) {
            ExpressionField field  = fieldStack.pop(); // pop the field associated with this value.
            FieldValuePair fieldValuePair = fieldValueTransformer.transformValue(field.stringValue(),value.value());
            if (fieldValuePair != null && fieldValuePair.getValue() != null) {
                value = new ExpressionValue(fieldValuePair.getValue());
            }
        }

        return DEFAULT_SQL_EXPRESSION_VALUE_VISITOR.visitExpressionValue(operator, value, data);
    }
}
