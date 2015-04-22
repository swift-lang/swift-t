// THIS-TEST-SHOULD-NOT-COMPILE

  
(int o) f (int i) {
  o = i;
}

// Check can't define conflicting type with function
type f {
  int a;
  int b;
}

main {
  int x = 3;
  trace(x);
}
