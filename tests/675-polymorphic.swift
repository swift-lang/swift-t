// SKIP-THIS-TEST
// COMPILE-ONLY-TEST
// Exercises a bug in inferring type of [];
import io;

app (file destination) scp(string args[], file source)
{
  "scp" args source destination;
}

app (file output) untar(file tarball)
{
  "tar" "xfz" tarball;
}

main
{
  host = "vesta.alcf.anl.gov:";

  tgz = "lmprun.tar.gz";
  file data_tgz = input_file(tgz);
  file remote_tgz<host/tgz> = scp([], data_tgz);
}

