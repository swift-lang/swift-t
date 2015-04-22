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

import exm.stc.common.exceptions.STCRuntimeError;

/**
 * Tcl builtin {*} operator that expands a list into multiple arguments
 * for function call.
 */
public class Expand extends Expression {
  
  private Expression expr;
  
  public Expand(Expression expr) {
    this.expr = expr;
  }
  
  @Override
  public void appendTo(StringBuilder sb, ExprContext mode) {
    if (mode != ExprContext.TCL_CODE) {
      throw new STCRuntimeError("Expand can't be used in " + mode);
    }
    sb.append("{*}");
    expr.appendTo(sb, mode);
  }

  @Override
  public boolean supportsStringList() {
    return false;
  }

}
