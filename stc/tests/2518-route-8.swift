// THIS-TEST-SHOULD-NOT-COMPILE

// Route: the key variable must be a string

string A[string] = { "k": "v" };
route (int t, A)
{
  default:
    trace(t);
}
