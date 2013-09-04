// SKIP-THIS-TEST

main {
  bag<string[]> string_bag;
  string_bag += ["hello", "world"];
  string_bag += ["mayday", "mayday"];
  string_bag += ["eins", "zwei", "drei", "vier", "fünf"];

  print_messages(string_bag);
}

import string;

print_messages(bag<string[]> msgs) {
  foreach msg in msgs {
    trace(string_join(msg, ", "));
  }
}
