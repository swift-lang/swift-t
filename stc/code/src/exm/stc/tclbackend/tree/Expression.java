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

public abstract class Expression extends TclTree
{
  @Override
  public final void appendTo(StringBuilder sb) {
    appendTo(sb, ExprContext.TCL_CODE);
  }
  
  public static enum ExprContext {
    TCL_CODE,
    VALUE_STRING,
    LIST_STRING,
  }
  
  /**
   * 
   * @param sb
   * @param mode how to escape expression  
   */
  public abstract void appendTo(StringBuilder sb, ExprContext mode);

  /**
   * @return true if we can include in a string that is valid tcl list
   */
  public abstract boolean supportsStringList();
}
