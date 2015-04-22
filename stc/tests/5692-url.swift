

app echo(url i)
{
  "echo" "echo:" i;
}

main() {
  url f = input_url("http://www.example.com");
  // Test that copying through unmapped temporary works.
  // This should be possible since we don't need to do a physical copy of
  // the URL
  url tmp = f;
  echo(tmp);
}
