import files;
import string;

app (file o) f(string s)
{
  "/bin/echo" s @stdout=o;
}

string lines[] = file_lines(input("mtc2.swift"));
foreach line,i in lines
{
  file y <sprintf("out-%i.txt",i)> = f(line);
}
