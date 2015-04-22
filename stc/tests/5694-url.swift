// THIS-TEST-SHOULD-NOT-COMPILE
// Check that we gracefully fail when user tries to copy url

app echo(url i)
{
  "echo" "echo:" i;
}

main() {
  url src = input_url("http://www.example.com");
  url dst<"http://destination.com"> = src;
  echo(dst);
}
