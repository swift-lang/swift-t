// Bug for app quoting

import files;
import assert;

app (file out) echo (string args[]) {
  "echo" args @stdout=out;
}

assertEqual("\"\n", read(echo(["\""])), "echo quote");
