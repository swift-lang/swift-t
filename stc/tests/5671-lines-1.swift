
import io;
import files;

main
{
  string A[] = file_lines(input_file("5671-lines.data"));
  foreach s, i in A
  {
    printf("string %i is %s", i, s);
  }
}
