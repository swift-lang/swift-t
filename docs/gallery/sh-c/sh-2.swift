import string;
import sys;

// Program configuration
string program;
if (getenv("HOST") == "umbra")
{
  program = "/bin/echo";
}
else
{
  // something else
}
// End program configuration

app f(string arguments)
{
  program arguments;
}
tokens = ["this","is","my","message"];
f(string_join(tokens," "));
