package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.Operator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface CustomFieldExpression {

    String generateExpression(
            BinaryExpression binaryExpression,
            String fieldName,
            String queryString,
            String resolvedOperator);

    default List<String> getIncludedFieldNames() {
        return Collections.EMPTY_LIST;
    }

    default List<Operator> getIncludedOperators() {
        return Collections.EMPTY_LIST;
    }

    default List<String> getExcludedFieldNames() {
        return Collections.EMPTY_LIST;
    }

    default List<Operator> getExcludedOperators()  {
        return Collections.EMPTY_LIST;
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
