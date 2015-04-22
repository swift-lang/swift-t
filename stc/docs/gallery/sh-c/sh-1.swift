import string;
app f(string commandline)
{
  "sh" "-c" commandline;
}
tokens = ["/bin/echo","this","is","my","message"];
f(string_join(tokens," "));
