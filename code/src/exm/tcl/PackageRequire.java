
package exm.tcl;

public class PackageRequire extends Command
{
  public PackageRequire(String pkg, String version)
  {
    super("package", "require", pkg, version);
  }
}
