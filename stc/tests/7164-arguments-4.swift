// THIS-TEST-SHOULD-NOT-RUN
// Supplying more positional arguments than arguments() declares is an
// error, reported at run time by the argp_check() that arguments()
// emits: "expected at most 1 arguments, received 2"

arguments(string s);

trace(s);
