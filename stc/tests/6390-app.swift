import files;
import assert;
import string;

// Test redirection

app (file out) echo (string arg) {
  "/bin/echo" arg @stdout=out;
}

app (file out) echostderr (string arg) {
  "./6390-echostderr.sh" arg @stderr=out
}

main () {
  string msg = "hello,world";
  file tmp = echo(msg);
  // echo appends newline
  assertEqual(read(tmp), msg + "\n", "contents of tmp");

  // Also write out to file for external checking
  file f<"6390.txt"> = echo(msg);

  // Unlike stdout above, stderr can also carry output we did not write,
  // such as dynamic loader warnings from the shell running the app.  So
  // require that the message is present, not that it is the whole file.
  file tmp2 = echostderr(msg);
  assert(find(read(tmp2), msg + "\n", 0, -1) >= 0, "contents of tmp2");
}
