#include <builtins.swift>
#include <sys.swift>
// THIS-TEST-SHOULD-NOT-RUN
main {
    string arg1 = argp(0);
    string arg2 = argp(1);
    // Invalid arg
    string arg3 = argp(2);
    trace(arg1, arg2, arg3);
}

