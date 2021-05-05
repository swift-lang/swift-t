
// Reported in issue #98
// This may have a race condition: 2021-05-03
// SKIP-THIS-TEST

int a[][];
foreach i in [1:50]
{
  foreach j in [1:50]
  {
    a[i][j]=3;
  }
}

int b[][];
foreach x,i in a
{
  foreach y,j in x
  {
    b[j][i]=a[i][j];
  }
}
