package com.intuit.graphql.filter.visitors;

public interface CustomExpressionResolver {

    default boolean contains(String fieldName, String operator)  { return false; }

    CustomFieldExpression resolve(String fieldName, String operator);
}
