// THIS-TEST-SHOULD-NOT-RUN
// directory() rejects a path that exists but is an ordinary file, which is
// the check that distinguishes it from input().

import files;

arguments(string d);

file dir = directory(d);

trace("unreachable " + filename(dir));
