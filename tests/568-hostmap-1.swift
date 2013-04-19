
import files;
import io;
import string;
import sys;

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
  location rank = hostmap_one(name);
  printf("rank: %i", rank);
}
