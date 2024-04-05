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

    public Operator(String name, Kind kind, String... type) {
        this.name = name;
        this.kind = kind;
        this.type = String.join("|", type);
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public Kind getKind() { return kind; }

    public enum Kind {
        COMPOUND, BINARY, UNARY
    }

    public static final String TYPE_LOGICAL = "Logical";
    public static final String TYPE_STRING = "String";
    public static final String TYPE_NUMERIC = "Numeric";
    public static final String TYPE_DATETIME = "DateTime";

    public static final Operator AND = new Operator("and", Operator.Kind.COMPOUND, TYPE_LOGICAL);
    public static final Operator OR = new Operator("or", Operator.Kind.COMPOUND, TYPE_LOGICAL);
    public static final Operator NOT = new Operator("not", Operator.Kind.UNARY, TYPE_LOGICAL);
    public static final Operator EQUALS = new Operator("equals", Operator.Kind.BINARY, TYPE_STRING);
    public static final Operator CONTAINS = new Operator("contains", Operator.Kind.BINARY, TYPE_STRING);
    public static final Operator STARTS = new Operator("starts", Operator.Kind.BINARY, TYPE_STRING);
    public static final Operator ENDS = new Operator("ends", Operator.Kind.BINARY, TYPE_STRING);
    public static final Operator EQ = new Operator("eq", Operator.Kind.BINARY, TYPE_NUMERIC);
    public static final Operator GT = new Operator("gt", Operator.Kind.BINARY, TYPE_NUMERIC);
    public static final Operator GTE = new Operator("gte", Operator.Kind.BINARY, TYPE_NUMERIC);
    public static final Operator LT = new Operator("lt", Operator.Kind.BINARY, TYPE_NUMERIC);
    public static final Operator LTE = new Operator("lte", Operator.Kind.BINARY, TYPE_NUMERIC);
    public static final Operator IN = new Operator("in", Operator.Kind.BINARY, TYPE_STRING, TYPE_NUMERIC);
    public static final Operator BETWEEN = new Operator("between", Operator.Kind.BINARY, TYPE_DATETIME, TYPE_NUMERIC);
}