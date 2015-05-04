import files;
import assert;

app (file out) echo (string args[]) {
  "echo" args @stdout=out;
}

assertEqual("\"", read(echo(["\""])), "echo quote");
