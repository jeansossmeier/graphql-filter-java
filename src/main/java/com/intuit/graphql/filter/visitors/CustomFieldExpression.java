package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;

public interface CustomFieldExpression {

    String getFieldName();

    String getOperator();

    String generateExpression(
            BinaryExpression binaryExpression,
            String fieldName,
            String queryString,
            String resolvedOperator);

    default LogicalOperator getEnclosingLogicalOperator() {
        return LogicalOperator.OR;
    }
}
