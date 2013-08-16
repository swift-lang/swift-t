
import files;
import io;
import string;
import location;

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
  /*string name = trim(read(tmp));
  printf("name: %s", name);
  location rank = hostmap_one(name);
  printf("rank: %i", rank);

  location worker_rank = hostmap_one_worker(name);
  printf("worker_rank: %i", rank);*/
}
