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
package com.intuit.graphql.filter.client;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.CompoundExpression;
import com.intuit.graphql.filter.ast.Expression;
import com.intuit.graphql.filter.ast.ExpressionField;
import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;
import com.intuit.graphql.filter.ast.OperatorRegistry;
import com.intuit.graphql.filter.ast.UnaryExpression;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses the given GraphQL filter expression Abstract Syntax Tree (AST)
 *
 * @author sjaiswal
 * @author jeansossmeier
 */
public class FilterExpressionParser {
    private static final String KIND_COMPOUND = Operator.Kind.COMPOUND.name();
    private static final String KIND_BINARY = Operator.Kind.BINARY.name();
    private static final String KIND_UNARY = Operator.Kind.UNARY.name();

    private final OperatorRegistry operatorRegistry;

    public FilterExpressionParser() {
        this.operatorRegistry = OperatorRegistry.withDefaultOperators();
    }

    public FilterExpressionParser(OperatorRegistry operatorRegistry) {
        this.operatorRegistry = operatorRegistry;
    }

    public Expression parseFilterExpression(Map filterArgs) {
        return createExpressionTree(filterArgs);
    }

    private Expression createExpressionTree(Map filterMap) {
        if (filterMap == null || filterMap.isEmpty() || filterMap.size() > 1) {
            return null;
        }

        final Deque<Expression> expressionStack = new ArrayDeque<>();
        final Set<Map.Entry> entries = filterMap.entrySet();

        Expression expression = null;
        for (Map.Entry entry : entries) {
            String key = entry.getKey().toString();
            if (isOperator(key)) {
                expression = handleExpression(entry, expressionStack, expression);
            } else {
                expression = handleFieldExpression(entry, key);
            }
        }

        return expression;
    }

    private Expression handleExpression(
            Map.Entry entry, Deque<Expression> expressionStack, Expression expression) {

        final String key = entry.getKey().toString();
        final String kind = getOperatorKind(key);
        if (KIND_COMPOUND.equals(kind)) {
            return handleCompound(expressionStack, entry, key);
        } else if (KIND_BINARY.equals(kind)) {
            return handleBinary(entry, key);
        } else if (KIND_UNARY.equals(kind)) {
            return handleUnary(entry, key);
        }

        return expression;
    }

    private Expression handleFieldExpression(Map.Entry entry, final String key) {
        final ExpressionField leftOperand = new ExpressionField(entry.getKey().toString());
        final BinaryExpression binaryExpression =
                (entry.getValue() instanceof Map)
                        ? (BinaryExpression)
                        createExpressionTree((Map) entry.getValue())
                        : (BinaryExpression) handleBinary(entry, Operator.IN.getKey());

        binaryExpression.setLeftOperand(leftOperand);
        return binaryExpression;
    }

    private Expression handleUnary(Map.Entry entry, String key) {
        final Expression operand = createExpressionTree((Map) entry.getValue());
        return new UnaryExpression(operand, getOperator(key), null);
    }

    private Expression handleBinary(Map.Entry entry, String key) {
        final BinaryExpression binaryExpression = new BinaryExpression();
        binaryExpression.setOperator(getOperator(key));
        if (entry.getValue() instanceof Collection) {
            final List<Comparable> expressionValues = new ArrayList<>();
            for (Comparable value : (List<Comparable>) entry.getValue()) {
                expressionValues.add(convertIfDate(value));
            }
            binaryExpression.setRightOperand(new ExpressionValue(expressionValues));
        } else {
            binaryExpression.setRightOperand(new ExpressionValue<>(convertIfDate((Comparable) entry.getValue())));
        }
        return binaryExpression;
    }

    private Expression handleCompound(
            Deque<Expression> expressionStack, Map.Entry entry, String key) {

        final List values = (List) entry.getValue();
        for (Object object : values) {
            Expression right = createExpressionTree((Map) object);
            Expression left = expressionStack.peek();
            if (validateExpression(right) && validateExpression(left)) {
                left = expressionStack.pop();
                expressionStack.push(
                        new CompoundExpression(left, getOperator(key), right));
            } else {
                expressionStack.push(right);
            }
        }

        return expressionStack.pop();
    }

    private Operator getOperator(String key) {
        return operatorRegistry.getOperator(key);
    }

    private String getOperatorKind(String key) {
        return getOperator(key).getKind().name();
    }

    private boolean isOperator(String key) {
        try {
            return operatorRegistry.getOperator(key) != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
    private Comparable convertIfDate(Comparable value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDate) {
            final LocalDate localDate = (LocalDate) value;
            return localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant();
        } else if (value instanceof LocalDateTime) {
            final LocalDateTime localDateTime = (LocalDateTime) value;
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } else if (value instanceof OffsetDateTime) {
            final OffsetDateTime offsetDateTime = (OffsetDateTime) value;
            return offsetDateTime.toInstant();
        }

        return value;
    }

    private boolean validateExpression(Expression expression) {
        if (expression == null) {
            return false;
        }

        return expression instanceof BinaryExpression
                || expression instanceof CompoundExpression
                || expression instanceof UnaryExpression;
    }

}

