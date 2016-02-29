import sys;

(int o) fib(int i)
{
  if (i >= 2)
  {
    o = fib(i-1) + fib(i-2);
  }
  else if (i == 1)
  {
    o = 1;
  }
  else
  {
    o = 0;
  }
}

int n = toint(argv("n"));
trace(fib(n));
