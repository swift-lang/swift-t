import string;

app (file o) sort(file i, file j)
{
  // This uses the standard sort -m feature (merge)
  "sort" "-mn" i j @stdout=o;
}

(file o) merge(int i, int j)
{
  if (j-i == 1)
  {
    file fi = input(sprintf("data-%i.txt",i));
    file fj = input(sprintf("data-%i.txt",j));
    o = sort(fi, fj);
  }
  else
  {
    d = j - i + 1;
    m = d %/ 2; // integer divide operator
    o = sort(merge(i,i+m-1), merge(i+m,j));
  }
}

file result <"sorted.txt"> = merge(0,7);
