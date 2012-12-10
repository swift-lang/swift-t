
#include <builtins.swift>

app () hostname()
{
  "/bin/hostname"
}

main
{
  foreach i in [0:9]
  {
    hostname();
  }
}
