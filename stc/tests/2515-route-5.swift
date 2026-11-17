
// "match" is only special as a route branch label: it is still an
// ordinary name elsewhere, even inside a route body

string match = "m";
int t = 3;
trace(match, t);

string A[string] = { "x": "1" };
route (string key, A) {
  match "x":
    trace("matched:", key, match);
}
