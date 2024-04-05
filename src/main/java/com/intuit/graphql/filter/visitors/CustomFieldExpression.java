package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.BinaryExpression;
import com.intuit.graphql.filter.ast.Operator;

public interface CustomFieldExpression {

    String getFieldName();

    Operator getOperator();

    String generateExpression(
            BinaryExpression binaryExpression,
            String fieldName,
            String queryString,
            String resolvedOperator);

    default LogicalOperator getEnclosingLogicalOperator() {
        return LogicalOperator.OR;
    }
}
