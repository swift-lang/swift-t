
package exm.stc.swigcbackend.tree;

public class PackageRequire extends Command
{
  public PackageRequire(String pkg, String version)
  {
    super("package", "require", pkg, version);
  }
}
