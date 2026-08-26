// THIS-TEST-SHOULD-NOT-COMPILE
// A boolean flag is true when given and false when not, so a default
// value for it is meaningless

flags(boolean b = true);

trace(b);
