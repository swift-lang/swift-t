
main {
  bag<string[]> string_bag;
  string_bag += ["hello", "world"];
  string_bag += ["mayday", "mayday"];
  string_bag += ["eins", "zwei", "drei", "vier", "fünf"];

  print_messages("string_bag", string_bag);

  print_messages("copy of string_bag", bag_copy(string_bag));
}

import string;

print_messages(string prefix, bag<string[]> msgs) {
  foreach msg in msgs {
    trace(prefix, string_join(msg, ", "));
  }
}

(bag <string[]> o) bag_copy (bag<string[]> i) {
  // Copy by value
  o = i; 
}
