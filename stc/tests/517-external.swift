// THIS-TEST-SHOULD-NOT-COMPILE

app (external o) echo (string msg[]) {
    "echo" "msg:" msg
}

main {
    external x = echo(["hello"]);
    void y = x;
    // Check that external type is specialization of void
    external z = y; // This should fail typechecking
}
