// THIS-TEST-SHOULD-NOT-COMPILE
// Every program that declares command line arguments accepts -h, so a
// flag of that name would be unreachable.

flags(bool h : "shadows the help flag");

trace(h);
