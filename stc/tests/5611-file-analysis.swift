(file o) f()
{
  // Remove next two lines to reproduce #128
  file r = input("5611-file-analysis.txt");
  o = r;
  // Note: file r does not exist!  But f() is unused.

  trace("file: " + filename(r));
}
