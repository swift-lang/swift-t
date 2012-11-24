package exm.stc.common;

import org.apache.log4j.Logger;

public class Logging {
  private static final String STC_LOGGER_NAME = "exm.stc";

  public static Logger getSTCLogger() {
    return Logger.getLogger(STC_LOGGER_NAME);
  }
}
