
#include <builtins.swift>
#include <files.swift>
#include <io.swift>
#include <string.swift>
#include <sys.swift>

/**
   Run as TURBINE_SRAND=<seed> turbine ... 568.tcl
*/

app (file o) hostname()
{
  "hostname" @stdout=o;
}

main
{
  file tmp<"tmp.txt"> = hostname();
  string name = trim(readFile(tmp));
  printf("name: %s", name);
  host_id rank = hostmap_one(name);
  printf("rank: %i", rank);
}
