
import io;
import unix;

file f1 = input("5601-f.txt");
file f2<"5601-copy.txt">;
void v = propagate(f2);
f2 = cp(f1);
v => printf("OK");
