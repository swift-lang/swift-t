package exm.stc.tclbackend;

import exm.stc.common.RequiredPackage;
import exm.stc.common.exceptions.STCRuntimeError;

public class TclPackage extends RequiredPackage {
  
  public final String name;
  
  public final String version;
  
  public TclPackage(String name, String version) {
    super();
    this.name = name;
    this.version = version;
  }
  
  @Override
  public String toString() {
    return this.name + "::" + this.version;
  }
  
  @Override
  public boolean equals(Object other) {
    if (!(other instanceof RequiredPackage)) {
      throw new STCRuntimeError("Bad class comparison: " +
                      other.getClass().getCanonicalName());  
    }
    
    RequiredPackage rp = (RequiredPackage)other;
    if (!(rp instanceof TclPackage)) {
      return false;
    }
    
    TclPackage tp = (TclPackage)rp;
    
    return tp.name.equals(this.name) && tp.version.equals(this.version);
  }

  @Override
  public int hashCode() {
    return 37 * name.hashCode() + version.hashCode();
  }
  
}
