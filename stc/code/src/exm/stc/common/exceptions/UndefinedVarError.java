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

package exm.stc.common.exceptions;

import java.util.Collection;

import exm.stc.frontend.Context;

public class UndefinedVarError
extends UserException
{
  public UndefinedVarError(Context context, String msg)
  {
    super(context, msg);
  }
  
  public static UndefinedVarError fromName(Context context, String varName) {
    return fromName(context, varName, null);
  }
  
  public static UndefinedVarError fromName(Context context, String varName,
          String extra) {
    String msg = "No variable called " + varName +
                 " was defined in this context.";
    if (extra != null) {
      msg += " " + extra;
    }
    return new UndefinedVarError(context, msg);
  }

  public static UndefinedVarError fromNames(Context context,
                                            Collection<String> varNames) {
    String namesString = "";
    boolean first = true;
    for (String varName: varNames) {
      if (!first) {
        namesString += ", ";
      }
      first = false;
      namesString += varName;
    }
    return new UndefinedVarError(context, "Variables with the "
        + "following names were undefined in this context: " + namesString);
  }
  
  private static final long serialVersionUID = 1L;
}
