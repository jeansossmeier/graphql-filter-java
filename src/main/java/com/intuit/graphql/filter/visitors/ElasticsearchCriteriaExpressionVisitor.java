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
import com.intuit.graphql.filter.ast.Expression;
import com.intuit.graphql.filter.ast.ExpressionField;
import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;
import com.intuit.graphql.filter.ast.UnaryExpression;
import com.intuit.graphql.filter.client.FieldValuePair;
import com.intuit.graphql.filter.client.FieldValueTransformer;
import org.springframework.data.elasticsearch.core.query.Criteria;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for traversing the expression tree and generating a compound elasticsearch {@link Criteria}
 * from it with correct precedence order.
 *
 * @author Sohan Lal
 */
public class ElasticsearchCriteriaExpressionVisitor implements ExpressionVisitor<Criteria> {

    private static final String QUOTE_CHARACTER = "\"";

    private final Map<String, String> fieldMap;
    private final Deque<String> fieldStack;
    private final FieldValueTransformer fieldValueTransformer;

    @FunctionalInterface
    public interface CriteriaStrategy {
        Criteria apply(Criteria criteria, String fieldName, Comparable<?> value);
    }

    private static final Map<Operator, CriteriaStrategy> MAPPINGS = new HashMap<>();

    static {
        MAPPINGS.put(Operator.STARTS, (criteria, fieldName, value) -> criteria.where(fieldName).startsWith(value.toString()));
        MAPPINGS.put(Operator.ENDS, (criteria, fieldName, value) -> criteria.where(fieldName).endsWith(value.toString()));
        MAPPINGS.put(Operator.CONTAINS, (criteria, fieldName, value) -> criteria.where(fieldName).contains(value.toString()));
        MAPPINGS.put(Operator.EQUALS, (criteria, fieldName, value) -> criteria.where(fieldName).expression("'" + value.toString() + "'"));
        MAPPINGS.put(Operator.LT, (criteria, fieldName, value) -> criteria.where(fieldName).lessThan(value));
        MAPPINGS.put(Operator.LTE, (criteria, fieldName, value) -> criteria.where(fieldName).lessThanEqual(value));
        MAPPINGS.put(Operator.GT, (criteria, fieldName, value) -> criteria.where(fieldName).greaterThan(value));
        MAPPINGS.put(Operator.GTE, (criteria, fieldName, value) -> criteria.where(fieldName).greaterThanEqual(value));
        MAPPINGS.put(Operator.EQ, (criteria, fieldName, value) -> criteria.where(fieldName).is(value));
        MAPPINGS.put(Operator.IN, (criteria, fieldName, value) -> criteria.where(fieldName).in((List<?>) value));
        MAPPINGS.put(Operator.BETWEEN, (criteria, fieldName, value) -> {
            List<?> values = (List<?>) value;
            return criteria.where(fieldName).greaterThanEqual(values.get(0)).lessThanEqual(values.get(1));
        });
    }

    public static void addStrategy(Operator operator, CriteriaStrategy strategy) {
        MAPPINGS.put(operator, strategy);
    }

    public static void removeStrategy(Operator operator) {
        MAPPINGS.remove(operator);
    }

    public ElasticsearchCriteriaExpressionVisitor(final Map<String, String> fieldMap, final FieldValueTransformer fieldValueTransformer) {
        this.fieldMap = fieldMap;
        this.fieldStack = new ArrayDeque<>();
        this.fieldValueTransformer = fieldValueTransformer;
    }

    /**
     * Returns the elastic criteria from the expression tree.
     *
     * @param expression The {@link Expression} instance.
     * @return A criteria to be used with {@link org.springframework.data.elasticsearch.core.query.Criteria}.
     */
    @Override
    public Criteria expression(final Expression expression) {
        Criteria criteria = null;
        if (expression != null) {
            criteria = expression.accept(this, null);
        }
        return criteria;
    }

    /**
     * Handles the processing of compound expression node.
     *
     * @param compoundExpression Contains compound expression.
     * @param data               Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public Criteria visitCompoundExpression(final CompoundExpression compoundExpression, final Criteria data) {
        Criteria result = null;
        Operator operator = compoundExpression.getOperator();
        if (Operator.AND.equals(operator)) {
            Criteria left = compoundExpression.getLeftOperand().accept(this, null);
            Criteria right = compoundExpression.getRightOperand().accept(this, null);
            result = left.and(right);
        } else if (Operator.OR.equals(operator)) {
            Criteria right;
            Criteria left;
            left = compoundExpression.getLeftOperand().accept(this, null);
            right = compoundExpression.getRightOperand().accept(this, null);
            result = left.or(right);
        }
        return result;
    }

    /**
     * Handles the processing of binary expression node.
     *
     * @param binaryExpression Contains binary expression.
     * @param data             Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public Criteria visitBinaryExpression(final BinaryExpression binaryExpression, final Criteria data) {
        final String fieldName = mappedFieldName(binaryExpression.getLeftOperand().infix());
        ExpressionValue<? extends Comparable> operandValue = (ExpressionValue<? extends Comparable>) binaryExpression.getRightOperand();
        operandValue = getTransformedValue(operandValue);
        return applyOperator(binaryExpression, data, fieldName, operandValue.value());
    }

    public Criteria applyOperator(BinaryExpression binaryExpression, Criteria data, String fieldName, Comparable<?> value) {
        CriteriaStrategy strategy = MAPPINGS.get(binaryExpression.getOperator());
        if (strategy == null) {
            throw new UnsupportedOperationException("Unsupported operator: " + binaryExpression.getOperator());
        }
        return strategy.apply(data, fieldName, value);
    }

    /**
     * Handles the processing of unary expression node.
     *
     * @param unaryExpression Contains unary expression.
     * @param data            Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public Criteria visitUnaryExpression(final UnaryExpression unaryExpression, final Criteria data) {
        final Criteria left = unaryExpression.getLeftOperand().accept(this, null);
        return left.not();
    }

    /**
     * Handles the processing of expression field node.
     *
     * @param field Contains expression field.
     * @param data  Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public Criteria visitExpressionField(final ExpressionField field, final Criteria data) {
        /* ExpressionField has been taken care in the Binary expression visitor. */
        return null;
    }

    /**
     * Handles the processing of expression value node.
     *
     * @param value Contains expression value.
     * @param data  Buffer for storing processed data.
     * @return Data of processed node.
     */
    @Override
    public Criteria visitExpressionValue(final ExpressionValue<? extends Comparable> value, final Criteria data) {
        /* ExpressionValue has been taken care in the Binary expression visitor. */
        return null;
    }

    private String mappedFieldName(final String fieldName) {
        final String mappedFieldName;
        if (fieldMap != null && fieldMap.get(fieldName) != null) {
            mappedFieldName = fieldMap.get(fieldName);
        } else if (fieldValueTransformer != null && fieldValueTransformer.transformField(fieldName) != null) {
            mappedFieldName = fieldValueTransformer.transformField(fieldName);
            fieldStack.push(fieldName); //pushing the field for lookup while visiting value.
        } else {
            mappedFieldName = fieldName;
        }
        return mappedFieldName;
    }

    private ExpressionValue getTransformedValue(final ExpressionValue<? extends Comparable> value) {
        if (!fieldStack.isEmpty() && fieldValueTransformer != null) {
            final String field = fieldStack.pop(); // pop the field associated with this value.
            final FieldValuePair fieldValuePair = fieldValueTransformer.transformValue(field, value.value());
            if (fieldValuePair != null && fieldValuePair.getValue() != null) {
                return new ExpressionValue(fieldValuePair.getValue());
            }
        }
        return value;
    }

}
