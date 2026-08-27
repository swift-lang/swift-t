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
package exm.stc.common.lang;

/**
 * The usage message generated from a program's arguments(), flags() or
 * main() parameter declarations, carried from the front end to the code
 * generator the same way CompileTimeArgs carries -A settings.
 *
 * The generated program prints this and exits when run with -h or --help.
 * That check has to happen eagerly, before turbine::start, because Swift/T
 * is a dataflow language: an equivalent check written in Swift would race
 * with the argp() calls the same declaration expands into.
 */
public class ProgramUsage {

  private static String usage = null;

  public static void set(String text) {
    usage = text;
  }

  /**
   * @return the usage message, or null if the program declares no
   *         command line arguments
   */
  public static String get() {
    return usage;
  }
}
