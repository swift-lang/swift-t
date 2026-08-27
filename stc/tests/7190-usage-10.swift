// THIS-TEST-SHOULD-NOT-COMPILE
// Documentation strings describe command line arguments, so they are only
// meaningful in arguments(), flags() and main() parameters.

(int o) f (int x : "not a command line argument") {
  o = x + 1;
}

trace(f(1));
