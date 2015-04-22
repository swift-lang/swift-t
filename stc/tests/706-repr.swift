// Test repr() functions
import assert;
import string;

(string o) lsort(string i) "turbine" "0.4.0" [
  "set <<o>> [ lsort <<i>> ]"
];

main () {
  // Check repr() works for various types
  assertEqual(repr(1), "1", "int");
  assertEqual(repr(" this is a string \n"), " this is a string \n", "string");
  assertEqual(repr(3.14), fromfloat(3.14), "float");
 
  // Bools are integers internally
  assertEqual(repr(true), "1", "bool");

  string S[] = array_repr([1,2,3]);
  assertEqual(S[0], "1", "S[0]");
  assertEqual(S[1], "2", "S[1]");
  assertEqual(S[2], "3", "S[2]");

  // Check we can convert to Tcl dict.  Order is undetermined
  string arrayRepr = repr(["quick", "brown", "fox"]);
  assert(find(arrayRepr, "0 quick", 0, -1) >= 0, "quick");
  assert(find(arrayRepr, "1 brown", 0, -1) >= 0, "brown");
  assert(find(arrayRepr, "2 fox", 0, -1) >= 0, "fox");
  
  bag<int> b;
  b += 321;
  b += 5;
  b += 60637;
  // Space separated list in Tcl, sorted in lexical order
  assertEqual(lsort(repr(b)), "321 5 60637", "bag");

  int A[string][string] = { "testing" : { "testing": 123 } };
  assertEqual(repr(A), "testing {testing 123}", "nested_assoc");

  // Check that we don't get tripped up by referencetypes
  string Z[][] = f();

  assertEqual(repr(Z[g(0)][g(1)]), "b", "repr");



}

(string o[][]) f() {
  o = [["a", "b", "c"]];
}

(int o) g(int i) {
  o = i;
}
