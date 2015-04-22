
// Check that string operations work ok in for loop

import string;

main {
  for (string s = "", int i = 0; i < 10;
       i = i + 1,
       s = sprintf("%s %i", s, i))
  {
    trace(i,s);
  }
}
