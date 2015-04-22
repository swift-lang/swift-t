/**
 Regression test for invalid location targeting.
 Needs to be run with more than one worker for testing!
 */

import assert;
import location;
import string;


@dispatch=WORKER
(string o) hello_worker(string msg) "turbine" "0.0.1" [
  "set mytmp1234 <<msg>>; set <<o>> \"Hello from [ adlb::rank ]: $mytmp1234\"; puts $<<o>>"
];

main {
  // Run multiple times to hopefully get one of the tasks sent to
  // the wrong worker in case of the bug
  foreach i in [1:100] {
    L1 = random_worker();
    act1 = test_fn(":)", L1);
    exp1 = "Hello from " + L1.rank + ": :)";
    trace("\"" + act1 + "\"") =>
    assertEqual(act1, exp1, "test 1");
  }
}

(string c) test_fn(string msg, location L)
{
   msg2 = sprintf("%s", msg);
   c = @location=L hello_worker(msg2);
}
