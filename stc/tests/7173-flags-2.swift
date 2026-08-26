// Flags may be omitted: a boolean flag is then false and the other
// types fall back to their declared defaults.  bool is accepted as a
// synonym for boolean inside flags() and arguments().

flags(bool loud, string s = "quiet", float f = 1.5, int i = -3);

trace(s, f, i);
if (loud) {
  trace("loud");
} else {
  trace("silent");
}
