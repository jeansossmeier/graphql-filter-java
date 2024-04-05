package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.Operator;

import java.util.List;

public interface CustomFieldExpression {

    String generateExpression(
            BinaryExpression binaryExpression,
            String fieldName,
            String queryString,
            String resolvedOperator);

    List<String> getFieldNames();

    List<Operator> getOperators();

    default LogicalOperator getEnclosingLogicalOperator() {
        return LogicalOperator.OR;
    }
}
