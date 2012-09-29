
// Test that assert works ok when they pass

#include <builtins.swift>
#include <assert.swift>

main {
    assert(true, "true");
    assertEqual(123, 123, "123 == 123");
    assertLT(1, 2, "1 < 2");
    assertLTE(2, 2, "2 <= 2");
}
