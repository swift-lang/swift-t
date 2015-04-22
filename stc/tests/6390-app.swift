import files;
import assert;

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

  file tmp2 = echostderr(msg);
  assertEqual(read(tmp2), msg + "\n", "contents of tmp2");
}
