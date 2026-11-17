// THIS-TEST-SHOULD-NOT-COMPILE

// Route: a branch may not write to the input array

string A[string];
A["k"] = "v";
route (string t, A)
{
  case "k":
    A["k2"] = t;
}
