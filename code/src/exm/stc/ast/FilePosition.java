package exm.stc.ast;

import java.util.Map.Entry;
import java.util.TreeMap;

/** 
 * Simple immutable class to record line/file
 * @author tga
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

  public static class LineMapping {
    private final TreeMap<Integer, FilePosition> fileMap
          = new TreeMap<Integer, FilePosition>();
    
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
