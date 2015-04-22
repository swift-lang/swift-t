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

package exm.stc.ui;

/**
 * Unix exit codes
 * @author wozniak
 * */
public enum ExitCode
{
  /** Successful exit */
  SUCCESS(0),
  /** Do not use this- reserved for JVM errors */
  ERROR_JAVA(1),
  /** I/O error */
  ERROR_IO(2),
  /** Failure in ANTLR parser code */
  ERROR_PARSER(3),
  /** Normal user errors */
  ERROR_USER(4),
  /** Bad command line argument */
  ERROR_COMMAND(5),
  /** Do not use this- reserved for wrapper script stc */
  ERROR_SCRIPT(6),
  /** Internal error in STC */
  ERROR_INTERNAL(90);
  
  final int code;

  ExitCode(int code)
  {
    this.code = code;
  }

  public int code()
  {
    return code;
  }
}
