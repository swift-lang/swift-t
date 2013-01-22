
#include <builtins.swift>
#include <files.swift>
#include <io.swift>
#include <random.swift>
#include <string.swift>
#include <sys.swift>

/**
   Run as turbine ... 568.tcl $RANDOM
*/

app (file o) hostname()
{
  "hostname" @stdout=o;
}

main
{
  void v = srand(toint(args()));
  wait (v)
  {
    file tmp<"tmp.txt"> = hostname();
    string name = trim(readFile(tmp));
    printf("name: %s", name);
    int rank = hostmap_one(name);
    printf("rank: %i", rank);
  }
}
