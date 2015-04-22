type person {
  string name;
  int age;
}

type megastruct {
  person people[];
  person person;
}

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

  wait deep (s) {
    trace("DONE");
  }
}
