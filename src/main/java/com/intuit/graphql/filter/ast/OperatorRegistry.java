/*
  Copyright 2020 Intuit Inc.
  Modifications Copyright 2024 Jean Luck Sossmeier

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/
package com.intuit.graphql.filter.ast;

import java.util.HashMap;
import java.util.Map;

import static com.intuit.graphql.filter.ast.Operator.AND;
import static com.intuit.graphql.filter.ast.Operator.BETWEEN;
import static com.intuit.graphql.filter.ast.Operator.CONTAINS;
import static com.intuit.graphql.filter.ast.Operator.ENDS;
import static com.intuit.graphql.filter.ast.Operator.EQ;
import static com.intuit.graphql.filter.ast.Operator.EQUALS;
import static com.intuit.graphql.filter.ast.Operator.GT;
import static com.intuit.graphql.filter.ast.Operator.GTE;
import static com.intuit.graphql.filter.ast.Operator.IN;
import static com.intuit.graphql.filter.ast.Operator.LT;
import static com.intuit.graphql.filter.ast.Operator.LTE;
import static com.intuit.graphql.filter.ast.Operator.NOT;
import static com.intuit.graphql.filter.ast.Operator.OR;
import static com.intuit.graphql.filter.ast.Operator.STARTS;

/**
 * Class that represents an operator registry
 *
 * @author jeansossmeier
 */
public class OperatorRegistry {
    private static final OperatorRegistry INSTANCE = new OperatorRegistry();

    public static OperatorRegistry defaultInstance() {
        return INSTANCE;
    }

    private final Map<String, Operator> operators = new HashMap<>();

    public void registerOperator(Operator operator) {
        operators.put(operator.getKey(), operator);
    }

    public Operator getOperator(String name) {
        final Operator operator = operators.get(name);
        if (operator == null) {
            throw new IllegalArgumentException("No operator found with name: " + name);
        }
        return operator;
    }

    public static OperatorRegistry withDefaultOperators() {
        final OperatorRegistry registry = OperatorRegistry.defaultInstance();

        // Logical Operators
        registry.registerOperator(AND);
        registry.registerOperator(OR);
        registry.registerOperator(NOT);

        // String Operators
        registry.registerOperator(EQUALS);
        registry.registerOperator(CONTAINS);
        registry.registerOperator(STARTS);
        registry.registerOperator(ENDS);

        // Numeric Operators
        registry.registerOperator(EQ);
        registry.registerOperator(GT);
        registry.registerOperator(GTE);
        registry.registerOperator(LT);
        registry.registerOperator(LTE);

        // Range Operators
        registry.registerOperator(IN);
        registry.registerOperator(BETWEEN);

        return registry;
    }

    public Map<String, Operator> getOperators() {
        return operators;
    }
}