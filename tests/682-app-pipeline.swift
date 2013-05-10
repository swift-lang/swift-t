
import io;
import string;

/*
 * Regression test for optimizer bug at O2 falsely assuming
 * filename variable for mapped output of app function was
 * closed
 */

app (file o) make_data(int sz)
{
  "./682-make-data.sh" sz o;
}

app (file o) wc(file i)
{
  "./682-wc.sh" i o;
}

main
{
  int sz = 10;
  int N = 3;
  for (int i = 0; i < N; i = i+1)
  {
    string data_name = sprintf("output-%i.data", i);
    file data<data_name>;
    data = make_data(sz);
    string count_name = sprintf("count-%i.data", i);
    file count<count_name>;
    count = wc(data);
  }
}
