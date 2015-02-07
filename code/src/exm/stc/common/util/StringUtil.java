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
package exm.stc.common.util;

import java.util.Arrays;
import java.util.List;

public class StringUtil {

  /**
   * Append the given number of spaces into given StringBuilder
   */
  public static void spaces(StringBuilder sb, int c) {
    for (int i = 0; i < c; i++)
      sb.append(' ');
  }

  public static String concat(List<String> tokens) {
    String[] array = new String[tokens.size()];
    tokens.toArray(array);
    return concat(array, 0, " ");
  }

  public static String concat(Object[] objs) {
    return concat(' ', objs);
  }
  public static String concat(char c, Object[] objs) {
    return concat(c, Arrays.asList(objs));
  }

  public static String concat(char c, List<? extends Object> objs) {
    String[] strings = new String[objs.size()];
    int i = 0;
    for (Object obj : objs)
      strings[i++] = obj.toString();
    return concat(c, strings);
  }

  public static String concat(String[] tokens, int start) {
    return concat(tokens, start, " ");
  }

  public static String concat(String[] tokens, int start, String separator) {
    if (tokens == null)
      return "null";
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < tokens.length; i++) {
      sb.append(tokens[i]);
      if (i < tokens.length - 1)
        sb.append(separator);
    }
    return sb.toString();
  }

  public static String concat(char c, String... strings) {
    if (strings == null)
      return "null";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < strings.length; i++) {
      sb.append(strings[i]);
      if (i < strings.length - 1)
        sb.append(c);
    }
    return sb.toString();
  }

  /**
   * Like String.trim(), but does not drop newlines. Thus correct for Tcl code.
   */
  public static String tclTrim(String s) {
    if (s.length() == 0)
      return s;
    // First, skip over leading spaces and tabs
    int i = 0;
    while (true) {
      if (i == s.length())
        break;
      char c = s.charAt(i);
      if (c != ' ' && c != '\t')
        break;
      i++;
    }
    // Next, drop trailing spaces and tabs
    int j = s.length() - 1;
    if (j < 0)
      j = 0;
    while (j >= 0) {
      if (j <= i)
        break;
      char c = s.charAt(j);
      if (c != ' ' && c != '\t')
        break;
      j--;
    }
    if (j < i)
      return "";
    return s.substring(i, j + 1);
  }

  /*
   * // Test for tclTrim(): use with od -c to check public static void
   * main(String[] args) { String[] cases = new String[] { "", "x", " x", "x ",
   * " x ", " x y ", " \n\t x y \t\n \n" }; for (String s : cases) { s =
   * tclTrim(s); System.out.print("[" + s + "]"); } }
   */
}
