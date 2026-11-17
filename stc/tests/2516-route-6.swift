// THIS-TEST-SHOULD-NOT-COMPILE

// Route: duplicate case labels

string A[string] = { "k": "v" };
route (string t, A) {
  case "k":
    trace(t);
  case "k":
    trace(t);
}
