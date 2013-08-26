
app echo(url i)
{
  "echo" "echo:" i;
}

main
{
  url f = input_url("ftp://host/path/file");
  echo(f);
}
