
/*
  MAP NORMAL
  Measure map rate
  Select a filesystem
*/

#include <builtins.swift>
#include <io.swift>
#include <string.swift>
#include <sys.swift>

app (file f) app_touch()
{
  "touch" @f;
}

app (file o) process(file i)
{
  "/bin/cp" @i @o;
}

main
{
  string user = "wozniak"; // getenv("USER");
  string fs = ".";
  // string fs = "/sandbox";
  // string fs = "/dev/shm";
  string tmpdir = fs + "/" + user + "/tmp";
  int N = toint(argv("N"));
  foreach i in [0:N-1]
  {
    // Input file name
    string ifname = sprintf("%s/tmp-%i.in", tmpdir, i);
    // Output file name
    string ofname = sprintf("%s/tmp-%i.out", tmpdir, i);

    file f1<ifname> = app_touch();
    file f2<ofname> = process(f1);
  }
}
