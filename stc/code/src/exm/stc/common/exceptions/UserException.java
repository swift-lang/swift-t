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

import exm.stc.frontend.Context;

/**
 * Represents an error caused by user input
 * Thus, this should contain good error message information
 * */
public class UserException
extends Exception
{
  public UserException(Context context, String message)
  {
    this(context.getInputFile(), context.getLine(),
         context.getColumn(), message);
  }
  
  public UserException(String file, int line, int col, String message) {
    super(file + ":" + line + ":" + (col > 0 ? (col + 1) + ":" : "") + 
          " " + message);
  }
  
  public UserException(String message) {
    super(message);
  }


  private static final long serialVersionUID = 1L;
}
