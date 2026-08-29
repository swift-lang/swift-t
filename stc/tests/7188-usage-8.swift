// THIS-TEST-SHOULD-NOT-COMPILE
// Every program that declares command line arguments accepts -h, so a
// flag of that name would be unreachable.

flags(boolean h : "shadows the help flag");

trace(h);
