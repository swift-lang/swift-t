// Reference counting regressino test with two nested for loops

main {
  int numDS = f();

  for (int i=0; i<numDS; i=i+1)
  {
    for (int j=0; j<numDS; j = j+1)
    {
      trace(i, j);
    }
  }
}

(int i) f() {
  i = 10;
}
