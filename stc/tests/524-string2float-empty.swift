// THIS-TEST-SHOULD-NOT-RUN
// The empty string is not a number.  Tcl's "string is double" accepts it
// unless -strict is given, which used to make string2float silently store
// an empty float; it now reports the same error as any other malformed
// number: "string2float(): could not convert string '' to float"
import sys;

trace(string2float(argv("f", "")));
