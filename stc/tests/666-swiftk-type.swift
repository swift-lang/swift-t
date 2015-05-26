
// THIS-TEST-SHOULD-CAUSE-WARNING
type blah;

app (blah out) make_blah () {
  "/bin/echo" 12345 @stdout=out
}

blah f<"666-out.tmp"> = make_blah();

import assert;
import files;
assertEqual(toint(read(f)), 12345, "");
