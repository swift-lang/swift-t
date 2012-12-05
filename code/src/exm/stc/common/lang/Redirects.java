package exm.stc.common.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents possible input/output redirections for a command line call
 * members are null for no redirection
 *
 */
public class Redirects<T> {
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
}
