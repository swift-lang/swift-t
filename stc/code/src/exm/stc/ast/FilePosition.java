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
package exm.stc.ast;

import java.util.Map.Entry;
import java.util.TreeMap;

/** 
 * Simple immutable class to record line/file position
 *
 */
public class FilePosition {
  public final String file;
  public final int line;
  
  
  public FilePosition(String file, int line) {
    super();
    this.file = file;
    this.line = line;
  }

  public String toString() {
    return file + ":" + line;
  }

  /**
   * Class used to keep track of correspondence between lines in 
   * CPP output and lines in original input files
   *
   */
  public static class LineMapping {
    private final TreeMap<Integer, FilePosition> fileMap
          = new TreeMap<Integer, FilePosition>();
    
    public static LineMapping makeSimple(String path) {
      LineMapping map = new LineMapping();
      // 1:1 mapping
      map.addPreprocInfo(1, path, 1);
      return map;
    }

    /**
     * 
     * @param preprocOutLine line of preprocessor output
     * @param origFile original input source
     * @param origLine corresponding line of input file
     */
    public void addPreprocInfo(int preprocOutLine, 
        String origFile, int origLine) {
      fileMap.put(preprocOutLine, new FilePosition(origFile, origLine));
    }
    
    /**
     * Given a line number from preprocessor output, work out 
     * where it is in the original file by interpolation.
     * @param preprocOutLine
     * @return
     */
    public FilePosition getFilePosition(int preprocOutLine) {
      Entry<Integer, FilePosition> preceding = 
                      fileMap.floorEntry(preprocOutLine);
      if (preceding == null) {
        return new FilePosition("<unknown>", preprocOutLine);
      } else {
        FilePosition precedingP = preceding.getValue();
        int diff = preprocOutLine - preceding.getKey();
        return new FilePosition(precedingP.file,
            precedingP.line + diff);
      }
    }
    public String toString() {
      return fileMap.toString();
    }
  }
}
