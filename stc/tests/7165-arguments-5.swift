// THIS-TEST-SHOULD-NOT-COMPILE
// Only string, int, float, boolean, file and url may be declared in
// arguments(); a command line argument is always text.

arguments(blob b);

trace("unreachable");
