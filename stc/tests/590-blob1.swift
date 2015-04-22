
import blob;

main {
  blob b;
  string s = "howdy";
  b = blob_from_string(s);
  string t = string_from_blob(b);
  trace(t);
}
