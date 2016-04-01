
import string;
import unix;

M = 3;
R = 3;

app (file o) generate_random_file()
{
  "dd" "if=/dev/urandom" "count=1" @stdout=o;
}

file b0<"bits-0.txt"> = generate_random_file();

file B[];
B[0] = b0;
foreach i in [1:R-1]
{
  file f[];
  foreach j in [0:M-1]
  {
    file g<sprintf("f-%i-%i.data",i,j)> = cp(B[i-1]);
    f[j] = g;
  }
  file b<sprintf("bits-%i.data",i)> = cat(f);
  B[i] = b;
}
