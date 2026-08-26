// arguments and flags are contextual keywords, not reserved words: they
// are recognised only as a statement introducing the declaration, and
// remain ordinary identifiers everywhere else.  unix.swift declares
// rm(string flags, string dirname), so reserving the word would break
// the standard library.

import io;
import unix;

// 'flags' as a user function name
(int y) flags (int x)
{
  y = x + 1;
}

// ...alongside the flags() declaration itself
flags(boolean loud);

main
{
  // 'arguments' as an ordinary variable name
  string arguments = "-rf";
  printf("rm %s %i %i", arguments, flags(6), loud);
}
