/*
  Copyright 2020 Intuit Inc.

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

import com.intuit.graphql.filter.visitors.ExpressionVisitor;

/**
 * Represents an expression value node
 * in the expression tree.
 *
 * @author sjaiswal
 * @author jeansossmeier
 */
public class ExpressionValue<V> implements Expression {

    private V value;

    public ExpressionValue(V value) {
        this.value = value;
    }

    /**
     * Returns the string field value.
     * @return
     */
    @Override
    public String infix() {
        final StringBuilder infix = new StringBuilder();
        if (value == null) {
            return null;
        }

        if (value instanceof Iterable) {
            for (V val : (Iterable<V>) value) {
                infix.append(val.toString()).append(",");
            }
            return infix.toString().isEmpty() ? "" : infix.substring(0, infix.length()-1);
        } else {
            infix.append(value);
            return infix.toString();
        }
    }

    /**
     * Returns the value.
     * @return
     */
    public V value() {
        return value;
    }

    /**
     * This method accepts a expression visitor and calls
     * the visit method on the visitor passing itself.
     * @param visitor
     *        ExpressionVisitor for traversing the expression tree.
     * @param data
     *        Buffer to hold the result of process node.
     * @param <T>
     * @return
     *        Returns the processed data.
     */
    @Override
    public <T> T accept(ExpressionVisitor visitor, T data) {
        return  (T)visitor.visitExpressionValue(this, data);
    }
}
