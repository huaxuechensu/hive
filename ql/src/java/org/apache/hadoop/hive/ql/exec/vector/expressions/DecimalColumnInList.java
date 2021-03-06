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

package org.apache.hadoop.hive.ql.exec.vector.expressions;

import org.apache.hadoop.hive.common.type.HiveDecimal;
import org.apache.hadoop.hive.ql.exec.vector.DecimalColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorExpressionDescriptor.Descriptor;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.util.DateTimeMath;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Output a boolean value indicating if a column is IN a list of constants.
 */
public class DecimalColumnInList extends VectorExpression implements IDecimalInExpr {
  private static final long serialVersionUID = 1L;
  private final int inputColumn;
  private HiveDecimal[] inListValues;

  // The set object containing the IN list.
  // We use a HashSet of HiveDecimalWritable objects instead of HiveDecimal objects so
  // we can lookup DecimalColumnVector HiveDecimalWritable quickly without creating
  // a HiveDecimal lookup object.
  private transient HashSet<HiveDecimalWritable> inSet;

  public DecimalColumnInList() {
    super();

    // Dummy final assignments.
    inputColumn = -1;
  }

  /**
   * After construction you must call setInListValues() to add the values to the IN set.
   */
  public DecimalColumnInList(int colNum, int outputColumnNum) {
    super(outputColumnNum);
    this.inputColumn = colNum;
  }

  @Override
  public void transientInit() throws HiveException {
    super.transientInit();

    inSet = new HashSet<HiveDecimalWritable>(inListValues.length);
    for (HiveDecimal val : inListValues) {
      inSet.add(new HiveDecimalWritable(val));
    }
  }

  @Override
  public void evaluate(VectorizedRowBatch batch) {

    if (childExpressions != null) {
      super.evaluateChildren(batch);
    }

    DecimalColumnVector inputColumnVector = (DecimalColumnVector) batch.cols[inputColumn];
    LongColumnVector outputColVector = (LongColumnVector) batch.cols[outputColumnNum];
    int[] sel = batch.selected;
    boolean[] nullPos = inputColumnVector.isNull;
    boolean[] outNulls = outputColVector.isNull;
    int n = batch.size;
    HiveDecimalWritable[] vector = inputColumnVector.vector;
    long[] outputVector = outputColVector.vector;

    // return immediately if batch is empty
    if (n == 0) {
      return;
    }

    outputColVector.isRepeating = false;
    outputColVector.noNulls = inputColumnVector.noNulls;
    if (inputColumnVector.noNulls) {
      if (inputColumnVector.isRepeating) {

        // All must be selected otherwise size would be zero
        // Repeating property will not change.
        outputVector[0] = inSet.contains(vector[0]) ? 1 : 0;
        outputColVector.isRepeating = true;
      } else if (batch.selectedInUse) {
        for(int j = 0; j != n; j++) {
          int i = sel[j];
          outputVector[i] = inSet.contains(vector[i]) ? 1 : 0;
        }
      } else {
        for(int i = 0; i != n; i++) {
          outputVector[i] = inSet.contains(vector[i]) ? 1 : 0;
        }
      }
    } else {
      if (inputColumnVector.isRepeating) {

        //All must be selected otherwise size would be zero
        //Repeating property will not change.
        if (!nullPos[0]) {
          outputVector[0] = inSet.contains(vector[0]) ? 1 : 0;
          outNulls[0] = false;
        } else {
          outNulls[0] = true;
        }
        outputColVector.isRepeating = true;
      } else if (batch.selectedInUse) {
        for(int j = 0; j != n; j++) {
          int i = sel[j];
          outNulls[i] = nullPos[i];
          if (!nullPos[i]) {
            outputVector[i] = inSet.contains(vector[i]) ? 1 : 0;
          }
        }
      } else {
        System.arraycopy(nullPos, 0, outNulls, 0, n);
        for(int i = 0; i != n; i++) {
          if (!nullPos[i]) {
            outputVector[i] = inSet.contains(vector[i]) ? 1 : 0;
          }
        }
      }
    }
  }

  @Override
  public Descriptor getDescriptor() {

    // This VectorExpression (IN) is a special case, so don't return a descriptor.
    return null;
  }

  public void setInListValues(HiveDecimal[] a) {
    this.inListValues = a;
  }

  @Override
  public String vectorExpressionParameters() {
    return getColumnParamString(0, inputColumn) + ", values " + Arrays.toString(inListValues);
  }

}
