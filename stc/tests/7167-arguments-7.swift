// THIS-TEST-SHOULD-NOT-COMPILE
// arguments() binds the command line of the program as a whole, so an
// imported module may not declare it -- only the main program may.

import include.import_7167;

trace("unreachable");
