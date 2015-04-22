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
public class ForLoop extends TclTree {
  private String loopVar;
  private Sequence loopBody;
  private Expression start;
  private Expression end; 
  private Expression incr; 

  public ForLoop(String loopVar, Expression start, Expression end, 
          Expression incr) {
    this(loopVar, start, end, incr, new Sequence());
  }
  
  /**
   * 
   * @param loopVar
   * @param start an integer Value or integer literal - start of range
   * @param end an integer Value or integer literal - end of range inclusive
   * @param incr an integer Value or integer literal - 
   *                amount to increment each iteration
   * @param loopBody
   */
  public ForLoop(String loopVar, Expression start, Expression end, 
      Expression incr, Sequence loopBody)
  {
    this.loopVar = loopVar;
    this.start = start;
    this.end = end;
    this.incr = incr;
    this.loopBody = loopBody;
  }

  public Sequence loopBody() {
    return loopBody;
  }
  
  @Override
  public void appendTo(StringBuilder sb)
  { 
    Value loopVarVal = new Value(loopVar);
    indent(sb);
    
    // E.g. for { set i 0 } { $i <= $n } { incr i $k } 
    sb.append("for ");
    // initializer
    sb.append("{ set " + loopVar + " " + start.toString() + " } ");
    // condition
    sb.append("{ " + loopVarVal.toString() + " <= " + end.toString() + " } "); 
    // next
    sb.append("{ incr " + loopVar + " " + incr.toString() + " } "); 
    
    loopBody.setIndentation(this.indentation);
    loopBody.appendToAsBlock(sb);
    sb.append("\n");
  }
}
