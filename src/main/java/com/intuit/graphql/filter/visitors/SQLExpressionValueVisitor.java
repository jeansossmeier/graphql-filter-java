package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.ast.ExpressionValue;
import com.intuit.graphql.filter.ast.Operator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLExpressionValueVisitor {
    public static final SQLExpressionValueVisitor DEFAULT = new SQLExpressionValueVisitor();

    private final Map<Operator, ExpressionValueHandler> expressionValueHandlers;

    public SQLExpressionValueVisitor() {
        expressionValueHandlers = new HashMap<>();
        expressionValueHandlers.put(Operator.CONTAINS, new ContainsHandler());
        expressionValueHandlers.put(Operator.STARTS, new StartsHandler());
        expressionValueHandlers.put(Operator.ENDS, new EndsHandler());
        expressionValueHandlers.put(Operator.BETWEEN, new BetweenHandler());
        expressionValueHandlers.put(Operator.IN, new InHandler());
    }

    public SQLExpressionValueVisitor(Map<Operator, ExpressionValueHandler> expressionValueHandlers) {
        this.expressionValueHandlers = expressionValueHandlers;
    }

    public String visitExpressionValue(
            Operator operator, ExpressionValue<? extends Comparable> expressionValue, String data) {
        final StringBuilder expressionBuilder = new StringBuilder(data);

        final ExpressionValueHandler handler = expressionValueHandlers.get(operator);
        if (handler != null) {
            handler.handle(operator, expressionBuilder, expressionValue);
        } else {
            expressionBuilder.append(resolveValue(expressionValue.value()));
        }

        return expressionBuilder.toString();
    }

    protected String resolveValue(Comparable value) {
        if (value instanceof Number) {
            return value.toString();
        } else {
            return "'" + value + "'";
        }
    }

    public class ContainsHandler implements ExpressionValueHandler {
        @Override
        public void handle(
                Operator operator,
                StringBuilder expressionBuilder,
                ExpressionValue<? extends Comparable> expressionValue) {

            final String value = expressionValue.infix();
            if (hasWildcardValue(value)) {
                expressionBuilder.append("'" + value + "'");
            } else {
                expressionBuilder.append("'%").append(value).append("%'");
            }
        }

        private boolean hasWildcardValue(String value) {
            return value.contains("%");
        }
    }

    public class StartsHandler implements ExpressionValueHandler {
        @Override
        public void handle(
                Operator operator,
                StringBuilder expressionBuilder,
                ExpressionValue<? extends Comparable> expressionValue) {

            expressionBuilder.append("'").append(expressionValue.infix()).append("%").append("'");
        }
    }

    public class EndsHandler implements ExpressionValueHandler {
        @Override
        public void handle(
                Operator operator,
                StringBuilder expressionBuilder,
                ExpressionValue<? extends Comparable> expressionValue) {

            expressionBuilder.append("'").append("%").append(expressionValue.infix()).append("'");
        }
    }

    public class BetweenHandler implements ExpressionValueHandler {
        @Override
        public void handle(
                Operator operator,
                StringBuilder expressionBuilder,
                ExpressionValue<? extends Comparable> expressionValue) {

            List<Comparable> expressionValues = (List<Comparable>)expressionValue.value();

            expressionBuilder
                    .append(resolveValue(expressionValues.get(0)))
                    .append(" AND ")
                    .append(resolveValue(expressionValues.get(1)));
        }
    }

    public class InHandler implements ExpressionValueHandler {
        @Override
        public void handle(
                Operator operator,
                StringBuilder expressionBuilder,
                ExpressionValue<? extends Comparable> expressionValue) {

            final List<Comparable> expressionValues = (List<Comparable>)expressionValue.value();
            expressionBuilder.append("(");

            for (int i = 0; i < expressionValues.size(); i++) {
                expressionBuilder.append(resolveValue(expressionValues.get(i)));
                if (i < expressionValues.size() - 1) {
                    expressionBuilder.append(", ");
                }
            }

            expressionBuilder.append(")");
        }
    }

    public interface ExpressionValueHandler {
        void handle(
                Operator operator,
                StringBuilder expressionBuilder,
                ExpressionValue<? extends Comparable> expressionValue);
    }
}
