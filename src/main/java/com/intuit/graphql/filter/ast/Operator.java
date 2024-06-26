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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Class of operators for supporting relational and logical expressions.
 *
 * @author sjaiswal
 * @author jeansossmeier
 */
public class Operator {
    private String key;
    private Kind kind;
    private List<String> types;
    private String type;

    public Operator(String key, Kind kind, String... types) {
        this.key = key;
        this.kind = kind;
        this.types = Arrays.asList(types);
        this.type = String.join("|", types);
    }

    public String getKey() { return key; }
    public String getType() { return type; }
    public List<String> getTypes() { return types; }
    public Kind getKind() { return kind; }

    public enum Kind {
        COMPOUND, BINARY, UNARY
    }

    public static final String TYPE_LOGICAL = "Logical";
    public static final String TYPE_STRING = "String";
    public static final String TYPE_NUMERIC = "Numeric";
    public static final String TYPE_DATETIME = "DateTime";
    public static final String TYPE_JSON = "Json";

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Operator operator = (Operator) o;
        return Objects.equals(key, operator.key) && Objects.equals(type, operator.type) && kind == operator.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, type, kind);
    }
}