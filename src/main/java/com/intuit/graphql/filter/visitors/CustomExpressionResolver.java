package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.Operator;

public interface CustomExpressionResolver {

    default boolean contains(String fieldName, Operator operator)  { return false; }

    CustomFieldExpression resolve(String fieldName, Operator operator);
}
