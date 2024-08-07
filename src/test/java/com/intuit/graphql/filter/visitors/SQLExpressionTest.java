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
package com.intuit.graphql.filter.visitors;

import com.intuit.graphql.filter.common.TestConstants;
import graphql.ExecutionResult;
import graphql.scalars.ExtendedScalars;
import graphql.schema.idl.RuntimeWiring;
import org.junit.Assert;
import org.junit.Test;

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;

/**
 * @author sjaiswal
 */
public class SQLExpressionTest extends BaseFilterExpressionTest {

    @Override
    public RuntimeWiring buildWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.DateTime)
                .type(newTypeWiring("Query")
                        .dataFetcher("searchEmployees", getEmployeeDataFetcher().searchEmployeesSQL()))
                .build();
    }
    @Test
    public void filterExpressionSimple() {
        ExecutionResult result = getGraphQL().execute(TestConstants.BINARY_FILER);

        String expectedExpression = "WHERE (empFirstName LIKE '%Saurabh%')";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionSimpleSingleDigitInt() {
        ExecutionResult result = getGraphQL().execute(TestConstants.BINARY_FILER_SINGLE_DIGIT_INT);

        String expectedExpression = "WHERE (age >= 5)";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionWithOR() {
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_FILER_WITH_OR);

        String expectedExpression = "WHERE ((empFirstName LIKE '%Saurabh%') OR (lastName = 'Jaiswal'))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionWithAND(){
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_FILER_WITH_AND);

        String expectedExpression = "WHERE ((empFirstName LIKE '%Saurabh%') AND (lastName = 'Jaiswal'))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionORWithAND() {
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_FILER_WITH_OR_AND);

        String expectedExpression = "WHERE ((empFirstName LIKE '%Saurabh%') OR ((lastName = 'Jaiswal') AND (age >= 25)))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionANDWithOR() {
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_FILER_WITH_AND_OR);

        String expectedExpression = "WHERE ((empFirstName LIKE '%Saurabh%') AND ((lastName = 'Jaiswal') OR (age >= 25)))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionANDWithMultipleOR() {
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_FILER_WITH_OR_OR_AND);

        String expectedExpression = "WHERE (((empFirstName LIKE '%Saurabh%') OR (lastName = 'Jaiswal')) OR ((empFirstName = 'Vinod') AND (age >= 30)))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionORWithMultipleAND() {
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_FILER_WITH_AND_AND_OR);

        String expectedExpression = "WHERE (((empFirstName LIKE '%Saurabh%') AND (lastName = 'Jaiswal')) AND ((empFirstName = 'Vinod') OR (age >= 30)))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionWithOtherArgs () {
        ExecutionResult result = getGraphQL().execute(TestConstants.FILTER_WITH_OTHER_ARGS);
        String parent = getEmployeeDataFetcher().getSqlExpression();

        String expectedExpression = "WHERE (empFirstName = 'Saurabh')";

        Assert.assertEquals(expectedExpression,parent);
    }

    @Test
    public void filterExpressionWithVariables () {
        ExecutionResult result = getGraphQL().execute(TestConstants.FILTER_WITH_VARIABLE);
        String parent = getEmployeeDataFetcher().getSqlExpression();

        String expectedExpression = "WHERE ((empFirstName LIKE '%Saurabh%') AND (lastName = 'Jaiswal'))";

        Assert.assertEquals(expectedExpression,parent);
    }

    @Test
    public void filterExpressionWithFirstNameAndLastNameQuotedEquals() {
        ExecutionResult result = getGraphQL().execute(TestConstants.FIRST_NAME_EQUALS_AND_LAST_NAME_CONTAINS_QUOTED);

        String expectedExpression = "WHERE ((empFirstName = 'Jais''wal') AND (lastName LIKE 'Jack o''%antern'''))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionWithLastNameIn () {
        ExecutionResult result = getGraphQL().execute(TestConstants.LAST_NAME_IN);

        String expectedExpression = "WHERE (lastName IN ('Jaiswal', 'Gupta', 'Kumar'))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionWithAgeIn () {
        ExecutionResult result = getGraphQL().execute(TestConstants.AGE_IN);

        String expectedExpression = "WHERE (age IN (32, 35, 40))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }


    @Test
    public void filterExpressionWithAgeBetween () {
        ExecutionResult result = getGraphQL().execute(TestConstants.AGE_BETWEEN);

        String expectedExpression = "WHERE (age BETWEEN 32 AND 40)";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void filterExpressionWithBirthDateBetween () {
        ExecutionResult result = getGraphQL().execute(TestConstants.BIRTH_DATE_BETWEEN);

        String expectedExpression = "WHERE (birthDate BETWEEN '1996-12-20T00:39:57Z' AND '2024-12-20T00:39:57Z')";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void invalidFilterExpression () {
        ExecutionResult result = getGraphQL().execute(TestConstants.INVALID_FILTER);

        String expectedExpression = null;

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void notFilterExpression() {
        ExecutionResult result = getGraphQL().execute(TestConstants.NOT_FILTER);

        String expectedExpression = "WHERE ( NOT (empFirstName = 'Saurabh'))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void notCompoundFilterExpression() {
        ExecutionResult result = getGraphQL().execute(TestConstants.NOT_COMPOUND_FILTER);

        String expectedExpression = "WHERE ( NOT ((empFirstName = 'Saurabh') AND (lastName LIKE '%Jaiswal%')))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void compoundFilterExpressionWithNot() {
        ExecutionResult result = getGraphQL().execute(TestConstants.COMPOUND_NOT_FILTER);

        String expectedExpression = "WHERE ((empFirstName = 'Saurabh') AND ( NOT (lastName LIKE '%Jaiswal%')))";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void compoundFilterExpressionWithStarts() {
        ExecutionResult result = getGraphQL().execute(TestConstants.FIRST_NAME_STARTS);

        String expectedExpression = "WHERE (empFirstName LIKE 'Sa%')";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void compoundFilterExpressionWithContains() {
        ExecutionResult result = getGraphQL().execute(TestConstants.FIRST_NAME_CONTAINS);

        String expectedExpression = "WHERE (empFirstName LIKE '%ura%')";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void compoundFilterExpressionWithEnds() {
        ExecutionResult result = getGraphQL().execute(TestConstants.FIRST_NAME_ENDS);

        String expectedExpression = "WHERE (empFirstName LIKE '%bh')";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }

    @Test
    public void firstNameSpecialCharacterExpressionWithEquals() {
        ExecutionResult result = getGraphQL().execute(TestConstants.FIRST_NAME_SPECIAL_CHAR_EQUALS);

        String expectedExpression = "WHERE (empFirstName = 'Máx''s \"')";

        Assert.assertEquals(expectedExpression,getEmployeeDataFetcher().getSqlExpression());
    }
}
