// Literal default values in arguments().  Only the first two arguments
// are supplied on the command line; the rest fall back to their defaults.
// A negative int literal and an int literal for a float argument are
// both accepted.

import io;

arguments(string s = "hi", int i = 10, float f = 2.5, boolean b = false,
          int neg = -3, float fi = 7);

printf("s=%s i=%i f=%.1f b=%s neg=%i fi=%.1f", s, i, f, b, neg, fi);
