
// Temporary test to ensure that top-level code doesn't cause runtime error.
// THIS-TEST-SHOULD-NOT-COMPILE

int i = 0;
trace(i);
j = i + 1;

trace (1) =>
  trace (2);


{ }

if (1) {

}

switch (1) {

}

foreach k in [1:i] {

}
