package exm.stc.common;

public class TclFunRef {
  
  public TclFunRef(String pkg, String symbol) {
    this(pkg, symbol, "0.0");
  }
  
  public TclFunRef(String pkg, String symbol, String version) {
    this.pkg = pkg;
    this.version = version;
    this.symbol = symbol;
  }
  public final String pkg;
  public final String version;
  public final String symbol;
}