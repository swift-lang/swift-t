
// Route: all branch kinds and an output array.
// Every matching branch runs; t is the matched key.

string A[string];
A["k1"] = "v1";
A["k2"] = "v2";
A["k3"] = "v3";
A["xy"] = "vxy";

route (string t, A, string B[string]) {
  case "k1":
    trace("k1a:", t, A[t]);
    trace("k1b:", t, A[t] + "b");
    B["k5"] = t + "b";
  case "k2":
    trace("k2:", t);
  pattern "[kj]+":
    trace("pattern:", t);
  match "k*":
    trace("match:", t);
  default:
    trace("default:", t);
}

trace(repr(B));
