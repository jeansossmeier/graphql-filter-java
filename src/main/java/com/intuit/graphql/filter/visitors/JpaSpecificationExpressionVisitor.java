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
import com.intuit.graphql.filter.ast.Operator;
import com.intuit.graphql.filter.ast.UnaryExpression;
import com.intuit.graphql.filter.ast.Expression;
import com.intuit.graphql.filter.ast.ExpressionField;
import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.client.FieldValuePair;
import com.intuit.graphql.filter.client.FieldValueTransformer;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is responsible for traversing
 * the expression tree and generating a compound
 * JPA Specification from it with correct precedence
 * order.
 *
 * @author sjaiswal
 * @author jeansossmeier
 */
public class JpaSpecificationExpressionVisitor<T> implements ExpressionVisitor<Specification<T>>{

    private Map<String, String> fieldMap;
    private Deque<String> fieldStack;
    private FieldValueTransformer fieldValueTransformer;

    @FunctionalInterface
    public interface PredicateStrategy<T> {
        Predicate buildPredicate(Root<T> root, CriteriaBuilder criteriaBuilder, Path path, ExpressionValue<?> value);
    }

    private final Map<Operator, PredicateStrategy<T>> mappings = new HashMap<>();

    public JpaSpecificationExpressionVisitor() {
        mappings.put(Operator.STARTS, (root, cb, path, value) -> cb.like(path, value.value() + "%"));
        mappings.put(Operator.ENDS, (root, cb, path, value) -> cb.like(path, "%" + value.value()));
        mappings.put(Operator.CONTAINS, (root, cb, path, value) -> cb.like(path, "%" + value.value() + "%"));
        mappings.put(Operator.EQUALS, (root, cb, path, value) -> cb.equal(path, value.value()));
        mappings.put(Operator.LT, (root, cb, path, value) -> cb.lessThan(path, (Comparable) value.value()));
        mappings.put(Operator.LTE, (root, cb, path, value) -> cb.lessThanOrEqualTo(path, (Comparable) value.value()));
        mappings.put(Operator.GT, (root, cb, path, value) -> cb.greaterThan(path, (Comparable) value.value()));
        mappings.put(Operator.GTE, (root, cb, path, value) -> cb.greaterThanOrEqualTo(path, (Comparable) value.value()));
        mappings.put(Operator.IN, (root, cb, path, value) -> path.in((List<Comparable>) value.value()));
        mappings.put(Operator.BETWEEN, (root, cb, path, value) -> {
            List<Comparable> values = (List<Comparable>) value.value();
            return cb.between(path, values.get(0), values.get(1));
        });
    }

    public Predicate resolvePredicate(Operator operator, Root<T> root, CriteriaBuilder cb, Path<String> path, ExpressionValue<?> value) {
        PredicateStrategy<T> strategy = mappings.get(operator);
        if (strategy == null) {
            throw new UnsupportedOperationException("Unsupported operator: " + operator);
        }
        return strategy.buildPredicate(root, cb, path, value);
    }

    public JpaSpecificationExpressionVisitor(Map<String, String> fieldMap, FieldValueTransformer fieldValueTransformer) {
        this.fieldMap = fieldMap;
        this.fieldStack = new ArrayDeque<>();
        this.fieldValueTransformer = fieldValueTransformer;
    }

    /**
     * Returns the JPA Specification from
     * the expression tree.
     * @return
     * @param expression
     */
    @Override
    public Specification<T> expression(Expression expression) {
        Specification<T> specification = null;
        if (expression != null){
            specification = expression.accept(this, null);
        }
        return specification;
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
    public Specification<T> visitCompoundExpression(CompoundExpression compoundExpression, Specification<T> data) {
        Specification<T> result = null;
        /* Logical operations.*/
        if (Operator.AND.equals(compoundExpression.getOperator())) {
            Specification<T> left = compoundExpression.getLeftOperand().accept(this, null);
            Specification<T> right = compoundExpression.getRightOperand().accept(this, null);
            result = Specification.where(left).and(right);
        } else if (Operator.OR.equals(compoundExpression.getOperator())) {
            Specification<T> right;
            Specification<T> left;
            left = compoundExpression.getLeftOperand().accept(this, null);
            right = compoundExpression.getRightOperand().accept(this, null);
            result = Specification.where(left).or(right);
        }
        return result;
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
    public Specification<T> visitBinaryExpression(BinaryExpression binaryExpression, Specification<T> data) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            ExpressionValue<? extends Comparable> operandValue = (ExpressionValue<? extends Comparable>)binaryExpression.getRightOperand();
            String fieldName = mappedFieldName(binaryExpression.getLeftOperand().infix());
            operandValue = getTransformedValue(operandValue);
            Path path = root.get(fieldName);
            return resolvePredicate(binaryExpression.getOperator(), root, criteriaBuilder, path, operandValue);
        };
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
    public Specification<T> visitUnaryExpression(UnaryExpression unaryExpression, Specification<T> data) {
        Specification<T> left = unaryExpression.getLeftOperand().accept(this, null);
        return Specification.not(left);
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
    public Specification<T> visitExpressionField(ExpressionField field, Specification<T> data) {
        /* ExpressionField has been taken care in the Binary expression visitor. */
        return null;
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
    public Specification<T> visitExpressionValue(ExpressionValue<? extends Comparable> value, Specification<T> data) {
        /* ExpressionValue has been taken care in the Binary expression visitor. */
        return null;
    }

    private String mappedFieldName(String fieldName) {
        StringBuilder expressionBuilder = new StringBuilder();
        if (fieldMap != null && fieldMap.get(fieldName) != null) {
            expressionBuilder.append(fieldMap.get(fieldName));
        } else if (fieldValueTransformer != null && fieldValueTransformer.transformField(fieldName) != null) {
            expressionBuilder.append(fieldValueTransformer.transformField(fieldName));
            fieldStack.push(fieldName); //pushing the field for lookup while visiting value.
        } else {
            expressionBuilder.append(fieldName);
        }
        return expressionBuilder.toString();
    }

    private ExpressionValue getTransformedValue(ExpressionValue<? extends Comparable> value) {
        if (!fieldStack.isEmpty() && fieldValueTransformer != null) {
            String field  = fieldStack.pop(); // pop the field associated with this value.
            FieldValuePair fieldValuePair = fieldValueTransformer.transformValue(field,value.value());
            if (fieldValuePair != null && fieldValuePair.getValue() != null) {
                value = new ExpressionValue(fieldValuePair.getValue());
            }
        }
        return value;
    }
}
