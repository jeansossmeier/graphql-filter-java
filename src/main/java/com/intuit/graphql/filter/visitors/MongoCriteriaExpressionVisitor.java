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
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for traversing the expression tree and
 * generating a compound mongo {@link Criteria} from it with correct precedence order.
 *
 * @author Sohan Lal
 * @author jeansossmeier
 */
public class MongoCriteriaExpressionVisitor<T> implements ExpressionVisitor<Criteria> {

    private static final String ANY_CHARACTERS = ".*";
    private static final String START_ANCHOR = "^";
    private static final String END_ANCHOR = "$";

    private final Map<String, String> fieldMap;
    private final Deque<String> fieldStack;
    private final FieldValueTransformer fieldValueTransformer;

    @FunctionalInterface
    public interface CriteriaStrategy {
        Criteria apply(String fieldName, ExpressionValue<? extends Comparable> value);
    }

    private static final Map<Operator, CriteriaStrategy> MAPPINGS = new HashMap<>();

    static {
        MAPPINGS.put(Operator.STARTS, (fieldName, value) ->
                Criteria.where(fieldName).regex(START_ANCHOR + value.value() + ANY_CHARACTERS));

        MAPPINGS.put(Operator.ENDS, (fieldName, value) ->
                Criteria.where(fieldName).regex(ANY_CHARACTERS + value.value() + END_ANCHOR));

        MAPPINGS.put(Operator.CONTAINS, (fieldName, value) ->
                Criteria.where(fieldName).regex(ANY_CHARACTERS + value.value() + ANY_CHARACTERS));

        MAPPINGS.put(Operator.EQUALS, (fieldName, value) ->
                Criteria.where(fieldName).is(value.value()));

        MAPPINGS.put(Operator.LT, (fieldName, value) ->
                Criteria.where(fieldName).lt(value.value()));

        MAPPINGS.put(Operator.LTE, (fieldName, value) ->
                Criteria.where(fieldName).lte(value.value()));

        MAPPINGS.put(Operator.EQ, (fieldName, value) ->
                Criteria.where(fieldName).is(value.value()));

        MAPPINGS.put(Operator.GT, (fieldName, value) ->
                Criteria.where(fieldName).gt(value.value()));

        MAPPINGS.put(Operator.GTE, (fieldName, value) ->
                Criteria.where(fieldName).gte(value.value()));

        MAPPINGS.put(Operator.IN, (fieldName, value) -> {
            List<Comparable> inValues = (List<Comparable>) value.value();
            return Criteria.where(fieldName).in(inValues);
        });

        MAPPINGS.put(Operator.BETWEEN, (fieldName, value) -> {
            List<Comparable> betweenValues = (List<Comparable>) value.value();
            return Criteria.where(fieldName).gte(betweenValues.get(0)).lte(betweenValues.get(1));
        });
    }

    public static Criteria getCriteria(Operator operator, String fieldName, ExpressionValue<? extends Comparable> value) {
        return MAPPINGS.getOrDefault(operator, (f, v) -> {
            throw new UnsupportedOperationException("Unsupported operator: " + operator);
        }).apply(fieldName, value);
    }

    public static void addCriteria(Operator operator, CriteriaStrategy function) {
        MAPPINGS.put(operator, function);
    }

    public static void removeCriteria(Operator operator) {
        MAPPINGS.remove(operator);
    }

    public MongoCriteriaExpressionVisitor(final Map<String, String> fieldMap, final FieldValueTransformer fieldValueTransformer) {
        this.fieldMap = fieldMap;
        this.fieldStack = new ArrayDeque<>();
        this.fieldValueTransformer = fieldValueTransformer;
    }

    /**
     * Returns the mongo criteria from the expression tree.
     *
     * @param expression The {@link Expression} instance.
     * @return A criteria to be used with {@link org.springframework.data.mongodb.core.query.Query}.
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
            result = new Criteria().andOperator(left, right);
        } else if (Operator.OR.equals(operator)) {
            Criteria right = compoundExpression.getRightOperand().accept(this, null);
            Criteria left = compoundExpression.getLeftOperand().accept(this, null);
            result = new Criteria().orOperator(left, right);
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
        final String fieldName = mappedFieldName(binaryExpression.getLeftOperand().stringValue());
        ExpressionValue<? extends Comparable> operandValue = (ExpressionValue<? extends Comparable>) binaryExpression.getRightOperand();
        operandValue = getTransformedValue(operandValue);
        return getCriteria(binaryExpression.getOperator(), fieldName, operandValue);
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
        return new Criteria().norOperator(left);
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
        String mappedFieldName;
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
