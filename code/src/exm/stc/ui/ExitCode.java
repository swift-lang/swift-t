
package exm.stc.ui;

/**
 * Unix exit codes
 * @author wozniak
 * */
public enum ExitCode
{
  /** Successful exit */
  SUCCESS(0),
  /** Do not use this- reserved for JVM errors */
  ERROR_JAVA(1),
  /** I/O error */
  ERROR_IO(2),
  /** Failure in ANTLR parser code */
  ERROR_PARSER(3),
  /** Normal user errors */
  ERROR_USER(4),
  /** Bad command line argument */
  ERROR_COMMAND(5),
  /** Internal error in STP */
  ERROR_INTERNAL(90),
  /** Do not use this- reserved for wrapper script stc */
  ERROR_SCRIPT(100);

  final int code;

  ExitCode(int code)
  {
    this.code = code;
  }

  public int code()
  {
    return code;
  }
}
