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
 * Loop over a range
 *
 */
public class WhileLoop extends TclTree {
  private Expression condition;
  private Sequence loopBody;
  
  public WhileLoop(Expression condition)
  {
    this(condition, new Sequence());
  }
  
  /**
   * 
   * @param loopBody
   */
  public WhileLoop(Expression condition, Sequence loopBody)
  {
    this.condition = condition;
    this.loopBody = loopBody;
  }
  
  public Sequence loopBody() {
    return loopBody;
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    
    // E.g. while { $i > 2 } 
    sb.append("while {");
    // condition
    condition.appendTo(sb);
    sb.append("} ");
    
    loopBody.setIndentation(this.indentation);
    loopBody.appendToAsBlock(sb);
    sb.append("\n");
  }
}
