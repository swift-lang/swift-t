
import io;
import string;
import sys;

argv_accept("v", "a", "exec", "help");

string program = argp(0);
printf("program: %s", program);

int c = argc();
printf("argc: %i", c);

string a = argv("a");
printf("argv a: %s", a);

string s1 = argp(1);
printf("argv 1: %s", s1);
string s2 = argp(2);
printf("argv 2: %s", s2);

string e = argv("exec");
string tokens[] = split(e, " ");
foreach t in tokens
{
  printf("token: %s", t);
}

if (argv_contains("v"))
{
  printf("has: v");
}

printf("args: %s", args());
