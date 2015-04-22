// THIS-TEST-SHOULD-NOT-COMPILE

f(int a, float b) {
  trace("got an int", a);
}

f(float a, int b) {
  trace("got a float", a);
}

/*
  Matching either overload requires converting an integer - no consistent
  way to unambigously resolve the overload, so we should fail and explain
  what happened.
 */

f(1, 1) unambigously resolve the overload, so we should fail and explain
what happened. */
f(1, 1);;
