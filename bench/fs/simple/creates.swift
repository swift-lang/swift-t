
/*
  SIMPLE CREATES
  Measure file create rate
  Select an ACTION
*/

#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

// Only choose one!
#define ACTION printf("i: %i", i)
//#define ACTION sleep(0)

main
{
  int N = toint(argv("N"));
  foreach i in [0:N-1]
  {
    ACTION;
  }
}
