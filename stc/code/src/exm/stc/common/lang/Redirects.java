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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents possible input/output redirections for a command line call
 * members are null for no redirection
 *
 */
public class Redirects<T> {
  /**
   * Keys to use when storing redirects in map
   */
  public static final String STDIN_KEY = "stdin";
  public static final String STDOUT_KEY = "stdout";
  public static final String STDERR_KEY = "stderr";

  public Redirects() {
    // Initialize to null
  }
  
  public Redirects(T stdin, T stdout, T stderr) {
    this.stdin = stdin;
    this.stdout = stdout;
    this.stderr = stderr;
  }
  
  
  /**
   *  Return all specified redirections
   * @param inputs
   * @param outputs
   * @return
   */
  public List<T> redirections(boolean inputs, boolean outputs) {
    ArrayList<T> res = new ArrayList<T>(3);
    if (inputs && stdin != null) {
      res.add(stdin);
    }
    if (outputs) {
      if (stdout != null) {
        res.add(stdout);
      }
      if (stderr != null) {
        res.add(stderr);
      }
    }
    return res;
  }
  
  public Redirects<T> clone() {
    return new Redirects<T>(stdin, stdout, stderr);
  }
  
  public String toString() {
    List<String> toks = new ArrayList<String>(3);
    if (stdin != null) { 
      toks.add("<" + stdin.toString());
    }
    if (stdout != null) {
      toks.add(">" + stdout.toString());
    }
    if (stderr != null) {
      toks.add("2>" + stderr.toString());
    }
    String res = "";
    boolean first = true;
    for (String tok: toks) {
      if (first) {
        first = false;
      } else {
        res += " ";
      }
      res += tok;
    }
    return res;
  }
  
  public T stdin = null;
  public T stdout = null;
  public T stderr = null;
  
  /**
   * Fill map with any present using keys "stdin" "stdout" "stderr"
   * @param taskProps
   */
  public void addProps(Map<String, T> taskProps) {
    if (stdin != null) {
      taskProps.put(STDIN_KEY, stdin);
    }
    
    if (stdout != null) {
      taskProps.put(STDOUT_KEY, stdout);
    }
    
    if (stderr != null) {
      taskProps.put(STDERR_KEY, stderr);
    }
  }
}
