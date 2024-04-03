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

/**
 * Class of operators for supporting relational and logical expressions.
 *
 * @author sjaiswal
 * @author jeansossmeier
 */
public class Operator {
    private String name;
    private String type;
    private Kind kind;

    public Operator(String name, String type, Kind kind) {
        this.name = name;
        this.type = type;
        this.kind = kind;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public Kind getKind() { return kind; }

    public enum Kind {
        COMPOUND, BINARY, UNARY
    }

    public static final Operator AND = new Operator("and", "Logical", Operator.Kind.COMPOUND);

    public static final Operator OR = new Operator("or", "Logical", Operator.Kind.COMPOUND);
    public static final Operator NOT = new Operator("not", "Logical", Operator.Kind.UNARY);
    public static final Operator EQUALS = new Operator("equals", "String", Operator.Kind.BINARY);
    public static final Operator CONTAINS = new Operator("contains", "String", Operator.Kind.BINARY);
    public static final Operator STARTS = new Operator("starts", "String", Operator.Kind.BINARY);
    public static final Operator ENDS = new Operator("ends", "String", Operator.Kind.BINARY);
    public static final Operator EQ = new Operator("eq", "Numeric", Operator.Kind.BINARY);
    public static final Operator GT = new Operator("gt", "Numeric", Operator.Kind.BINARY);
    public static final Operator GTE = new Operator("gte", "Numeric", Operator.Kind.BINARY);
    public static final Operator LT = new Operator("lt", "Numeric", Operator.Kind.BINARY);
    public static final Operator LTE = new Operator("lte", "Numeric", Operator.Kind.BINARY);
    public static final Operator IN = new Operator("in", "String|Numeric", Operator.Kind.BINARY);
    public static final Operator BETWEEN = new Operator("between", "DateTime|Numeric", Operator.Kind.BINARY);
}