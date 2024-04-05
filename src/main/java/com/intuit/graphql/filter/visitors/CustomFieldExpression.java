package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.Operator;

import java.util.Arrays;
import java.util.List;

public interface CustomFieldExpression {

    String generateExpression(
            BinaryExpression binaryExpression,
            String fieldName,
            String queryString,
            String resolvedOperator);

    default List<String> getIncludedFieldNames() {
        return List.of();
    }

    default List<Operator> getIncludedOperators() {
        return List.of();
    }

    default List<String> getExcludedFieldNames() {
        return List.of();
    }

    default List<Operator> getExcludedOperators()  {
        return List.of();
    }

    default LogicalOperator getEnclosingLogicalOperator() {
        return LogicalOperator.OR;
    }

    default <T> List<T> include(T... objects) {
        return Arrays.asList(objects);
    }

    default <T> List<T> exclude(T... objects) {
        return Arrays.asList(objects);
    }
}
