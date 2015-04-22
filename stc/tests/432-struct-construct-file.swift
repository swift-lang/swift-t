// SKIP-THIS-TEST

import assert;

type s1 {
  file f;
  string s;
  int A[];
}

type s2 {
  s1 struct;
  string s;
  int A[];
}

test();

test () {
  s = s2(s1(input("432.txt"), "hello", build_array(42)), "world", build_array(1));
  s2 = s.struct;

  assertEqual(s2.s + " " + s.s + " " + s2.A[0], "hello world 42", "Test 1");

  test_call(s);
}

test_call(s2 s) {
  assertEqual(size(s.A), 3, "s.A size");
  assertEqual(s.A[0], 1, "s.A[0]");
  assertEqual(s.A[1], 2, "s.A[1]");
  assertEqual(s.A[2], 3, "s.A[2]");

  assertEqual(filename(s.s.f), "432.txt");
}

(int A[]) build_array(int start) {
  A = [start, start + 1, start + 2];
}
