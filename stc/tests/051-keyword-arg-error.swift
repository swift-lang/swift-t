// THIS-TEST-SHOULD-NOT-COMPILE

/* Not allow to specify required arg with keyword */

myfunction(int x) {
  trace(x);
}

myfunction(x=1);
