
/*
  SIMPLE CREATES
  Measure file create rate
  Select an ACTION
  Select a filesystem
*/

#include <builtins.swift>
#include <io.swift>
#include <string.swift>
#include <sys.swift>

// Only choose one!
// #define ACTION printf("i: %i", i)
// #define ACTION app_sleep()
#define ACTION file f<fname> = app_touch();

app () app_sleep()
{
  "sleep" "0";
}

app (file f) app_touch()
{
  "touch" @f;
}

main
{
  string user = "wozniak"; // getenv("USER");
  // string fs = ".";
  // string fs = "/sandbox";
  string fs = "/dev/shm";
  string tmpdir = fs + "/" + user + "/tmp";
  int N = toint(argv("N"));
  foreach i in [0:N-1]
  {
    string fname = sprintf("%s/tmp-%i.out", tmpdir, i);
    ACTION;
  }
}
