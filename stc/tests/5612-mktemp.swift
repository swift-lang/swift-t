import files;

string s = mktemp_string();
trace(s);

(file o, void v) g()
{
  o = mktemp();
  v = trace(filename(f)); // Or Python
}

report(file f)
{
  trace(filename(f));
}

file f;
void v;
(f, v) = g();
propagate(f,v) => report(f);
