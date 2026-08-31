// THIS-TEST-SHOULD-NOT-RUN
// A directory argument given an ordinary file is rejected when the argument
// is bound, not by whatever first tries to use it as a directory.

arguments(directory d);

trace("unreachable " + filename(d));
