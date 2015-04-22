
// Exercises a bug in inferring type of [];
// Also tests the dircat operator

import io;

app (file dir) mkdir ()
{
  "mkdir" "-p" dir
}

app (file destination) cp(string args[], file source)
{
  "cp" args source destination;
}

app (file output) untar(file tarball)
{
  "tar" "xfz" tarball;
}

main
{
  dirname = "dir_675";
  tgz = "test_675.tgz";
  data_tgz = input_file(tgz);

  file dir<dirname> = mkdir() =>
  file copy_tgz<dirname/tgz> = cp([], data_tgz);
}

