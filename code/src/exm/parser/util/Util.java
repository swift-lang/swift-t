
package exm.parser.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Miscellaneous utility functions
 * @author wozniak
 * */
public class Util
{
  static DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

  /**
     @return Current time formatted as human-readable String
   */
  public static String timestamp()
  {
    return df.format(new Date());
  }

  public static String stackTrace(Throwable e)
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    return sw.toString();
  }

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

  public static boolean arrayContains(Object[] a, Object o)
  {
    for (Object x : a)
    {
      if (x.equals(o))
        return true;
    }
    return false;
  }
}
