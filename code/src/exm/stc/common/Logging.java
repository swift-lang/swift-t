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
package exm.stc.common;

import java.util.HashSet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import exm.stc.common.util.Pair;

public class Logging {
  private static final String STC_LOGGER_NAME = "exm.stc";

  /**
   * Messages already emitted.
   */
  private static final HashSet<Pair<org.apache.log4j.Level, String>> emitted =
          new HashSet<Pair<org.apache.log4j.Level, String>>(); 
  
  public static Logger getSTCLogger() {
    return Logger.getLogger(STC_LOGGER_NAME);
  }
  
  /**
   * @param level
   * @param msg
   * @return true if not already emitted
   */
  public static boolean addEmitted(org.apache.log4j.Level level, String msg) {
    return emitted.add(Pair.create(level, msg));
  }

  public static void uniqueWarn(String msg) {
    if (Logging.addEmitted(Level.WARN, msg)) {
      Logging.getSTCLogger().warn(msg); 
    } else {
      Logging.getSTCLogger().debug("Duplicate Warning: " + msg);
    }
  }
}
