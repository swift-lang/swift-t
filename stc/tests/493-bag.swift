import random;
import assert;

main {
  bag<string> F1, F2;

  if (randint(0, 10) < 11) {
    // Branch should be taken

    // Check sequencing
    trace("BEFORE") =>
        F1 += "string1" =>
        trace("AFTER"); 
  } else {
    trace("FAILURE1");
  }

  if (randint(0, 10) > 100) {
    // Branch should not be taken
    trace("FAILURE2");
  } else {
    F2 += "string2";
  }

  foreach f1 in F1 {
    trace("F1",f1);
  }
  foreach f2 in F2 {
    trace("F2",f2);
  }

  assertEqual(bag_size(F1), 1, "size(F1)");
  assertEqual(bag_size(F2), 1, "size(F2)");
}

