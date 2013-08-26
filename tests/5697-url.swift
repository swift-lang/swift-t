// We should be able to write a function that does something like copy a URL
import assert;

app (url o) copy_url(url i)
{
  // Simulate copying a url
  "echo" "copy_url" i o;
}

main() {
  // This should be valid
  url f<"ftp://example.com/"> = copy_url(input_url("http://example.com"));

  assertEqual(urlname(f), "ftp://example.com/", "url");
}
