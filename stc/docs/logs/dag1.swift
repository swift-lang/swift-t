
import unix;

void v1, v2;
v1 = sleep(1);
wait (v1) { v2 = sleep(1); }
wait (v2) { sleep(1); }
