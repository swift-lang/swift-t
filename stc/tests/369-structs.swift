// COMPILE-ONLY-TEST
// Regression for incorrect error assigning struct on branch

type complex {
  float re;
  float im;
}

(complex o) flip (complex i) {
  o.re = i.im;
  o.im = i.re;
}

main () {
  
  complex x;
  complex y;
  x.re = 1;
  x.im = 0;
  if (1)
  {
    y = flip(x);
  }
  else
  {
    y.im = 1;
    y.re = 1;
  }
}
