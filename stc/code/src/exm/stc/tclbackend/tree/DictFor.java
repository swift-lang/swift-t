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
 * Foreach construct
 * 
 * @author armstrong
 * */
public class DictFor extends Sequence
{
  Expression list;
  private Token loopValVar;
  private Token loopKeyVar;
  private TclTree loopBody;

  public DictFor(Token loopKeyVar, Token loopValVar, 
            Expression list, TclTree loopBody) {
    this.loopKeyVar = loopKeyVar;
    this.loopValVar = loopValVar;
    this.list = list;
    this.loopBody = loopBody;
    members.add(loopBody);
  }

  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);
    sb.append("dict for {");
    loopKeyVar.appendTo(sb);
    sb.append(" ");
    loopValVar.appendTo(sb);
    sb.append("} ");
    list.appendTo(sb);
    sb.append(" ");
    loopBody.setIndentation(this.indentation);
    loopBody.appendToAsBlock(sb);
    sb.append("\n");
  }
  

}
