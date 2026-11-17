
// Route: chain routes through stages in a loop.
// Each stage fills a fresh output array A1 from A[i], then assigns it
// whole to A[i+1], so it is closed as soon as its branches finish and
// the next stage can start.

string A[int][string];
A[0]["k1"] = "v1";
A[0]["k2"] = "v2";
A[0]["k3"] = "v3";
A[0]["xy"] = "vxy";

// [0:3] is inclusive: stages 0 to 3 write A[1] to A[4]
foreach i in [0:3]
{
  string A0[string] = A[i];
  string A1[string];
  route (string t, A0, A1)
  {
    pattern "k+":
      trace("pattern: ", i, " : ", t);
      string k_new = t + "," + i;
      A1[k_new] = A0[t] + ",v" + i;
  }

  A[i+1] = A1;
}

trace("A[0]: ", repr(A[0]));
trace("A[3]: ", repr(A[3]));
