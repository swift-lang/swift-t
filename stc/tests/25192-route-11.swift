//THIS-TEST-SHOULD-NOT-COMPILE

// Route: an existing output variable named in the header must be
// used in some branch, so that a misspelled name is reported

string A[string] = { "k1": "v1" };
string B[string];
B["x"] = "y";

route (string t, A, Bee) {
  case "k1":
    trace(t);
}
