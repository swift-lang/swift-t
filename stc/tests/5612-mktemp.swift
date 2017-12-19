import files;

string s = mktemp_string();
trace(s);

(file o, void w) g()
{
  o = mktemp();
  w = trace(filename(o)); // Or Python
}

report(file r)
{
  trace(filename(r));
}

file f;
void v;
(f, v) = g();
propagate(f,v) => report(f);
