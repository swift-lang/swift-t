import files;
import string;
import unix;

app (file o) g(string s)
{
  "/bin/echo" s @stdout=o;
}

main
{
  string lines[] = file_lines(input("mtc2.swift"));
  file fragments[];
  foreach line,i in lines
  {
    file y <sprintf("out-%i.txt",i)> = g(line);
    fragments[i] = y;
  }
  file result <"assembled.txt"> = cat(fragments);
}
