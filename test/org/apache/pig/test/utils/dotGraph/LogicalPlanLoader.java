/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pig.test.utils.dotGraph;

import org.apache.pig.impl.logicalLayer.*;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.logicalLayer.parser.QueryParser ;
import org.apache.pig.impl.logicalLayer.parser.ParseException ;
import org.apache.pig.impl.io.FileSpec;
import org.apache.pig.builtin.PigStorage;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Map;


public class LogicalPlanLoader
                extends OperatorPlanLoader<LogicalOperator, LogicalPlan> {

    /***
     * Create various Logical Operators
     * @param node
     * @param plan
     * @return
     */
    protected LogicalOperator createOperator(DotNode node, LogicalPlan plan) {
        String operatorType = node.attributes.get("type") ;

        // Cannot work without the correct type
        if (operatorType == null) {
            throw new RuntimeException("Unspecified operator type from Dot file") ;
        }

        if (operatorType.equals("LOLoad")) {
            return createLOLoad(node, plan) ;
        }
        else if (operatorType.equals("LOFilter")) {
            return createLOFilter(node, plan) ;
        }
        else if (operatorType.equals("LODistinct")) {
            return createLODistinct(node, plan) ;
        }
        else if (operatorType.equals("LOSort")) {
            return createLOSort(node, plan) ;
        }
        else if (operatorType.equals("LOForEach")) {
            return createLOForEach(node, plan) ;
        }
        else if (operatorType.equals("LOSplit")) {
            return createLOSplit(node, plan) ;
        }
        else if (operatorType.equals("LOSplitOutput")) {
            return createLOSplitOutput(node, plan) ;
        }
        else if (operatorType.equals("LOCogroup")) {
            return createLOCogroup(node, plan) ;
        }
        else if (operatorType.equals("LOForEach")) {
            return createLOForEach(node, plan) ;
        }
        else if (operatorType.equals("LOUnion")) {
            return createLOUnion(node, plan) ;
        }
        else if (operatorType.equals("LOCross")) {
            return createLOCross(node, plan) ;
        }

        // else
        throw new AssertionError("Unknown operator type") ;
    }

    private LOLoad createLOLoad(DotNode node, LogicalPlan plan) {
        LOLoad load = null ;
        FileSpec fileSpec = new FileSpec("pi",
                                         PigStorage.class.getName()) ;
        try {
            load = new LOLoad(plan, getKey(node.attributes), fileSpec, null) ;
            fillSchema(load, node.attributes) ;
        }
        catch (IOException ioe) {
            throw new AssertionError("Dummy data is not good") ;
        }
        return load ;
    }

    private LOFilter createLOFilter(DotNode node, LogicalPlan plan) {
        LOFilter filter = new LOFilter(plan, getKey(node.attributes), null, null) ;
        fillSchema(filter, node.attributes) ;
        return filter ;
    }

    private LODistinct createLODistinct(DotNode node, LogicalPlan plan) {
        LODistinct distinct = new LODistinct(plan, getKey(node.attributes), null) ;
        fillSchema(distinct, node.attributes) ;
        return distinct ;
    }

    private LOSort createLOSort(DotNode node, LogicalPlan plan) {
        LOSort sort = new LOSort(plan, getKey(node.attributes),
                                 null, null, null, "") ;
        fillSchema(sort, node.attributes) ;
        return sort ;
    }

    private LOForEach createLOForEach(DotNode node, LogicalPlan plan) {
        LOForEach foreach = new LOForEach(plan, getKey(node.attributes), null, null) ;
        fillSchema(foreach, node.attributes) ;
        return foreach ;
    }

    private LOSplit createLOSplit(DotNode node, LogicalPlan plan) {
        LOSplit split = new LOSplit(plan, getKey(node.attributes),  null) ;
        fillSchema(split, node.attributes) ;
        return split ;
    }

    private LOSplitOutput createLOSplitOutput(DotNode node, LogicalPlan plan) {
        LOSplitOutput splitOut = new LOSplitOutput(plan,
                                        getKey(node.attributes), 0,  null) ;
        fillSchema(splitOut, node.attributes) ;
        return splitOut ;
    }

    private LOCogroup createLOCogroup(DotNode node, LogicalPlan plan) {
        LOCogroup cogroup = new LOCogroup(plan, getKey(node.attributes),
                                          null, null, null) ;
        fillSchema(cogroup, node.attributes) ;
        return cogroup ;
    }

    private LOUnion createLOUnion(DotNode node, LogicalPlan plan) {
        LOUnion union = new LOUnion(plan, getKey(node.attributes),  null) ;
        fillSchema(union, node.attributes) ;
        return union ;
    }

    private LOCross createLOCross(DotNode node, LogicalPlan plan) {
        LOCross cross = new LOCross(plan, getKey(node.attributes),  null) ;
        fillSchema(cross, node.attributes) ;
        return cross ;
    }

    private void fillSchema(LogicalOperator op, Map<String,String> attributes) {
        String schemaString = attributes.get("schema") ;
        if (schemaString != null) {

            ByteArrayInputStream stream
                    = new ByteArrayInputStream(schemaString.getBytes()) ;
            QueryParser queryParser = new QueryParser(stream) ;
            Schema schema = null ;
            try {
                schema = queryParser.TupleSchema() ;
                op.forceSchema(schema);
                op.setSchemaComputed(true);
            }
            catch (ParseException pe) {
                System.out.println(pe.getMessage()) ;
                throw new RuntimeException("Error reading schema string") ;
            }
        }
        else {
            op.forceSchema(null);
        }
    }
}
