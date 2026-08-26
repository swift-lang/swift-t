// THIS-TEST-SHOULD-NOT-RUN
// Giving a flag that flags() does not declare is an error, reported at
// run time by the argv_accept() that flags() emits:
// "unknown flag: -bogus  accepted flags are: -a"

flags(int a = 0);

trace(a);
