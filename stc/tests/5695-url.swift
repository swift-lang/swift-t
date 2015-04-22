// THIS-TEST-SHOULD-NOT-COMPILE
// Doesn't make sense to redirect stdout to a url

app (url o) echo(url i)
{
  "echo" "echo:" i @stdout=o;
}

main() {
  // This should be valid
  url f<"ftp://example.com/"> = echo(input_url("http://example.com"));
}
