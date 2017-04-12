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
 * Tcl catch construct
 *
 * This captures the whole thing:
 *
 * if [ catch { body } exceptionVariable ] { 
 *   handler
 * }
 * 
 * @author wozniak
 * April 5, 2017
 * */
public class Catch extends TclTree
{
  private Sequence body;
  private String exceptionVariable;
  private Sequence handler;


  public Catch(Sequence body, String exceptionVariable, Sequence handler)
  {
    this.body    = body;
    this.exceptionVariable = exceptionVariable;
    this.handler = handler;
  }
  
  @Override
  public void appendTo(StringBuilder sb)
  {
    indent(sb);

    Square ctch =
             new Square(
               new Command(new Token("catch"),
                           new Text("{ "),
                           body,
                           new Text(" } "),
                           new Text(exceptionVariable)));
    If if_stmt = new If(ctch, handler);

    if_stmt.appendTo(sb);
    sb.append("\n");
  }
}
