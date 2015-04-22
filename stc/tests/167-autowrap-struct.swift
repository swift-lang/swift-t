// Check wrapping and recursive wait for struct with nested arrays
type person {
  string name;
  int age;
}

type megastruct {
  person people[];
  person person;
}

(string o) fmt(megastruct s) "turbine" "0" [
  "set <<o>> <<s>>"
];

import assert;

main {
  megastruct s;
  s.person.name = "Bob";
  s.person.age = 100;
 
  person t;
  t.name = "X";
  t.age = 1;
  s.people[0] = t;
  s.people[1] = s.person;
  s.people[2] = t;

  // TODO: check format
  trace(fmt(s));
}
