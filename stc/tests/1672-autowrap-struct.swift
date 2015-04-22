// Check returning structs with nested arrays
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

(megastruct o) fmt(person person, person ppl[]) "turbine" "0" [
  "set <<o>> [ dict create person <<person>> people <<ppl>>]"
];

(uberstruct o) fmt2(person person, person ppl[]) "turbine" "0" [
  "set <<o>> [ dict create mega [ dict create person <<person>> people <<ppl>> ] person <<person>> ]"
];

import assert;

main {
  person p1, p2;
  p1.name = "Bob";
  p1.age = 100;
  p2.name = "Jane";
  p2.age = 101;

  person ppl[] = [p1, p2, p1];

  assertEqual(fmt(p1, ppl).person.name, "Bob", "a");
  assertEqual(fmt(p1, ppl).person.age, 100, "b");
  assertEqual(fmt(p1, ppl).people[0].name, "Bob", "c");
  assertEqual(fmt(p1, ppl).people[1].name, "Jane", "d");

  assertEqual(fmt2(p1, ppl).person.name, "Bob", "e");
  assertEqual(fmt2(p1, ppl).person.age, 100, "f");
  
  assertEqual(fmt2(p1, ppl).mega.people[0].name, "Bob", "g");
  assertEqual(fmt2(p1, ppl).mega.people[1].name, "Jane", "h");
}
