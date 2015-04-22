
// Test string split()

import string;

main {
    string x = "hello world";
    trace(x);
    string p = "/bin:/home/user/evil path/d2/d1:/usr/bin";
    trace(p);

    {
      string tokens[];
      tokens = split(x, " ");
      foreach t in tokens {
        trace(t);
      }
    }

    {
      string tokens[];
      tokens = split(p, ":");
      foreach t in tokens {
        trace(t);
      }
    }
}

