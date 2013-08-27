// THIS-TEST-SHOULD-NOT-COMPILE
// Check that we can't provide unmapped URL as output arg

app (url o) echo(url i)
{
  "echo" "echo:" i o;
}

main() {
  // This doesn't make sense, as we can't allocate a temporary URL
  url f = echo(input_url("http://www.example.com"));
}
