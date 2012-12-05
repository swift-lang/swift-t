package exm.stc.common.lang;

/**
 * Represents possible input/output redirections for a command line call
 * members are null for no redirection
 *
 */
public class Redirections {
  public Redirections() {
    // Initialize to null
  }
  
  public Redirections(Arg stdin, Arg stdout, Arg stderr) {
    this.stdin = stdin;
    this.stdout = stdout;
    this.stderr = stderr;
  }
  
  public Redirections clone() {
    return new Redirections(stdin, stdout, stderr);
  }
  public Arg stdin = null;
  public Arg stdout = null;
  public Arg stderr = null;
}
