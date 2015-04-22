import sys;

main {
    sleep(0.5) => 
        trace("after 0.5");

    float y;
    int x = 1 =>
        trace("start") =>
        sleep(1) =>
        trace("after 1") =>
        y = 0.5 =>
        sleep(y) =>
        trace("after 1.5");
}
