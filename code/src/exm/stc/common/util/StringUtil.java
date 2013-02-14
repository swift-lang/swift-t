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
     Return a String containing the given number of spaces
   */
  public static String spaces(int c)
  {
    StringBuilder sb = new StringBuilder(c);
    spaces(sb, c);
    return sb.toString();
  }

  /**
     Append the given number of spaces into given StringBuilder
   */
  public static void spaces(StringBuilder sb, int c)
  {
    for (int i = 0; i < c; i++)
      sb.append(' ');
  }

  public static String concat(List<String> tokens)
  {
    String[] array = new String[tokens.size()];
    tokens.toArray(array);
    return concat(array, 0, " ");
  }

  public static String concat(String... strings)
  {
    return concat(' ', strings);
  }

  public static String concat(Object[] objs)
  {
    String[] strings = new String[objs.length];
    int i = 0;
    for (Object obj : objs)
      strings[i++] = obj.toString();
    return concat(' ', strings);
  }
  
  public static String concat(String[] tokens, int start)
  {
    return concat(tokens, start, " ");
  }

  public static String concat(String[] tokens, int start,
                              String separator)
  {
    if (tokens == null)
      return "null";
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < tokens.length; i++)
    {
      sb.append(tokens[i]);
      if (i < tokens.length - 1)
        sb.append(separator);
    }
    return sb.toString();
  }

  public static String concat(char c, String... strings)
  {
    if (strings == null)
      return "null";
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < strings.length; i++)
    {
      sb.append(strings[i]);
      if (i < strings.length - 1)
        sb.append(c);
    }
    return sb.toString();
  }

  /**
     Put a variable number of Strings into a List of Strings
   */
  public static List<String> stringList(String... args)
  {
    List<String> result = Arrays.asList(args);
    return result;
  }

}
