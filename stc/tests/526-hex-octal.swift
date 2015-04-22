// Hex literal support
import assert;
main {
  assertEqual(0x3041, 12353, "Hex Check 1");
  assertEqual(-0x1f414, -128020, "Hex Check 2");

  assertEqual(0o123, 83, "Octal Check 1");
}
