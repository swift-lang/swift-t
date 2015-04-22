// THIS-TEST-SHOULD-NOT-RUN
// Cannot copy urls

main () {
  url x<"somewhere">;

  x = somewhere_else();
}

(url out) somewhere_else() {
  
  out = input_url("somewhere_else");
}
