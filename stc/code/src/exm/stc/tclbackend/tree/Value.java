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

package exm.stc.tclbackend.tree;

/**
 * Simply dereference variable as $variable
 * @author wozniak
 * */
public class Value extends Expression
{
  private String variable;
  
  /** if true, add braces as appropriate for context */
  private boolean treatAsList = false;
  
  /** if false, can't include in string list */
  private boolean supportsStringList = false;

  public Value(String variable, boolean treatAsList, boolean supportsStringList)
  {
    this.variable = variable;
    this.treatAsList = treatAsList;
    this.supportsStringList = supportsStringList;
  }
  
  public Value(String variable)
  {
    this(variable, false, false);
  }
  
  public String variable() {
    return this.variable;
  }
  
  public static Value numericValue(String variable) {
    return new Value(variable, false, true);
  }
  
  public void setTreatAsList(boolean val) {
    this.treatAsList = val;
  }
  
  public void setSupportsStringList(boolean val) {
    this.supportsStringList = val;
  }

  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    boolean brace = mode == ExprContext.LIST_STRING && treatAsList;
    
    if (mode == ExprContext.LIST_STRING) {
      // Check that we can safely include in list
      assert(supportsStringList) : this;
    }
    
    if (brace)
        sb.append("{");
    // enclose in {} to allow a wider range of characters to be used in 
    // var names, such as :
    sb.append("${");
    sb.append(variable);
    sb.append("}");
    if (brace)
      sb.append("}");
  }
  

  @Override
  public boolean supportsStringList() {
    return supportsStringList;
  }
}
