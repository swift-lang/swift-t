
app echo(file i)
{
  "echo" i;
}

main
{
  file f = input_url("ftp://host/path/file");
  echo(f);
}
