import sys;

string prog = argp(0);
string arg1 = argp(1);
string arg2 = argp(2, "default");
string arg3 = argp(3, "default");
trace(prog, arg1, arg2, arg3);
