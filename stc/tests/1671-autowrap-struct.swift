// Check wrapping and recursive wait for struct with nested arrays and structs
type person {
  string name;
  int age;
}

type megastruct {
  person people[];
  person person;
}

type uberstruct {
  person person;
  megastruct mega;
}

(string o) fmt(megastruct s) "turbine" "0" [
  "set <<o>> <<s>>"
];

(string o) fmt2(uberstruct s) "turbine" "0" [
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

  trace("test1",fmt(s));

  uberstruct s2;
  s2.mega.person = s.person;
  s2.mega.people = s.people;
  s2.person.name = "Jane";
  s2.person.age = 101;

  trace("test2",fmt2(s2));
}
