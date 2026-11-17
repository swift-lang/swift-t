
// Route: a case branch runs as soon as its key arrives, before the
// input array is closed.  Here the array is only closed after the
// branch has run, so waiting for the close would deadlock.
// A case for a key that never arrives is skipped without error.

string A[string];
A["k1"] = "v1";

route (string t, A, string B[string]) {
  case "k1":
    B["seen"] = t;
  case "absent":
    trace("ABSENT-SHOULD-NOT-RUN");
}

// The last key of A depends on the case branch
A["k2"] = B["seen"] + "-later";
trace("k2:", A["k2"]);
