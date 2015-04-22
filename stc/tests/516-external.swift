// COMPILE-ONLY-TEST
// Check that external type passes typechecking, etc.

app (external o) echo (string msg[]) {
    "echo" "msg:" msg
}

app () f(void i) {
    "echo" "hello"
}

main {
    echo(["one"]) =>
        echo(["one", "two"]) =>
        echo(["one", "two", "three"]);
    
    external x = echo(["hello"]);
    f(x);
    wait(x) {
        trace("test");
    }

}
