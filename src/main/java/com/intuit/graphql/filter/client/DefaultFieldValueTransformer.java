/*
  Copyright 2021 Intuit Inc.

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

package com.intuit.graphql.filter.client;

public class DefaultFieldValueTransformer implements FieldValueTransformer {

    @Override
    public String transformField(String fieldName) {
        return fieldName;
    }

    @Override
    public FieldValuePair<? extends Object> transformValue(String fieldName, Object value) {
        return new FieldValuePair<>(fieldName, value);
    }
}

