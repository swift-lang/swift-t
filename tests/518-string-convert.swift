
/*
 * Test conversion from numeric to string types
 */
import assert;

main () {
  assertEqual(1 + "1", "11", "int + string");
  assertEqual("24" + 1, "241", "string + int");

  assertEqual(1.0 + "1", "1.01", "float + string");
  assertEqual("24" + 1.5, "241.5", "string + float");

  assertEqual(strcat(1, 1.25, "hello"), "11.25hello", "strcat");
}
