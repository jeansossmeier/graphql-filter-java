package com.intuit.graphql.filter.visitors;


public enum LogicalOperator {
    AND("AND"),
    OR("OR");

    private final String value;

    LogicalOperator(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

