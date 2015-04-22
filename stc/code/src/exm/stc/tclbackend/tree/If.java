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
 * If-then construct
 * To include else-if-then, else-then blocks, we need to extend this
 *
 * @author wozniak
 * */
public class If extends TclTree
{
  private TclTree condition;
  private Sequence thenBlock;
  private Sequence elseBlock;

  public If(TclTree condition, Sequence thenBlock, Sequence elseBlock)
  {
    this.condition = condition;
    this.thenBlock = thenBlock;
    this.elseBlock = elseBlock;
  }

  public If(Expression condition, Sequence thenBlock)
  {
    this((TclTree) condition, thenBlock, null);
  }

  /**
     The user must do an add() later to set the then-block
   */
  public If(Expression condition, boolean hasElse)
  {
    this((TclTree) condition, new Sequence(),
             hasElse ? new Sequence() : null);
  }
  
  public Sequence thenBlock() {
    return thenBlock;
  }
  
  public Sequence elseBlock() {
    return elseBlock;
  }

  @Override
  public void appendTo(StringBuilder sb)
  {

    indent(sb);
    sb.append("if { ");
    condition.appendTo(sb);
    sb.append(" } ");
    // then block
    thenBlock.setIndentation(indentation);
    thenBlock.appendToAsBlock(sb);
    if (elseBlock != null) {
      sb.append(" else ");
      // else block
      elseBlock.setIndentation(indentation);
      elseBlock.appendToAsBlock(sb);
    }
    sb.append("\n");
  }
  

}
