
package exm.stc.common.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Miscellaneous utility functions
 * @author wozniak
 * */
public class Misc
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
