// THIS-TEST-SHOULD-NOT-RUN
// directory() rejects a path that is not there at all, as input() does.

import files;

arguments(string d);

file dir = directory(d);

trace("unreachable " + filename(dir));
