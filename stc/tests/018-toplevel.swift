import io;

/* Check support for referencing top-level variable from other functions*/

string name = "Juan";

say_hello();


say_hello() {
  printf("HELLO %s", name);
}
