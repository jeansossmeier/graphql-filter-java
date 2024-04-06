package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;

public interface ExpressionValueHandler {
    void handle(
            Operator operator,
            StringBuilder expressionBuilder,
            ExpressionValue<? extends Comparable> expressionValue);
}