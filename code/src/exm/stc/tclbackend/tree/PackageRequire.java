
package exm.stc.tclbackend.tree;

public class PackageRequire extends Command
{
  public PackageRequire(String pkg, String version)
  {
    super("package", "require", pkg, version);
  }
}
