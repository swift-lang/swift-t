/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package exm.stc.frontend;

import exm.stc.common.util.Counters;

public class FunctionContext {

  private final String functionName;
  private final Counters<String> counters;

  public FunctionContext(String functionName) {
    this.functionName = functionName;
    this.counters = new Counters<String>();
  }

  public String getFunctionName() {
    return functionName;
  }

  /**
   * For any given string key, return integers
   * in a sequence starting from 1
   * @param counterName
   * @return
   */
  public long getCounterVal(String counterName) {
    return counters.increment(counterName);
  }

  /**
   * A way to automatically generate unique names.
   * returns something like:
   * <function name>-<construct type>-<unique number>
   * @param constructType
   * @return
   */
  public String constructName(String constructType) {
    return this.getFunctionName() + "-" + constructType +
                  getCounterVal(constructType);
  }

}
