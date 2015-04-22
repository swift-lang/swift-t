
// Check wrapping combined with passing in struct
type person {
  string name;
  int age;
}


/*
  Have a variant of this function for every possible form of
  wrapping.  All should behave identically
 */

// Waiting on ability to pass structs to inline tcl code
(string o) fmt_person_wrapped(person p) "funcs_165" "0.5" [
  "set <<o>> [ funcs_165::fmt_person <<p>> ]"
];

(string o) fmt_person_both(person p) "funcs_165" "0.5" "fmt_person_async" [
  "set <<o>> [ funcs_165::fmt_person <<p>> ]"
];

(string o) fmt_person_async(person p) "funcs_165" "0.5" "fmt_person_async";


import assert;

main {
  person bob;
  bob.name = "Bob";
  bob.age = 100;

  assertEqual("Name: Bob Age: 100", fmt_person_wrapped(bob), "wrapped");
  assertEqual("Name: Bob Age: 100", fmt_person_async(bob), "async");
  assertEqual("Name: Bob Age: 100", fmt_person_both(bob), "both");
}
