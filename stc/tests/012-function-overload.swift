

f(int a) {
  trace("got an int", a);
}

f(float a) {
  trace("got a float", a);
}

int x = 1;
f(x);

float y = 1.0;
f(y);
