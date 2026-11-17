
// Route: the header may name an existing output variable

string A[string] = { "k1": "v1", "k2": "v2" };
string B[string];

route (string t, A, B) {
  case "k1":
    B[t] = A[t] + "!";
  match "k*":
    B[t + "-m"] = A[t];
}

trace(B["k1"], B["k1-m"], B["k2-m"]);
