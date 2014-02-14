import sys;

/*
 * Regression test for bug where implicit dependency between apps is
 * not respected
 */

app (file implicit) produce(string dir)
{
  "./683-produce.sh" dir
}

app (void o) consume(string dir, file implicit)
{
  "./683-consume.sh" dir
}

main {
  string dir = "tmp-683";
  file f<dir/"tmp.txt">;

  foreach i in [1:10] {
    // Consume should execute after produce is done
    consume(dir, f) =>
      trace("DONE", i);
  }
  // Try to force produce to execute after consume
  wait(sleep(0.1)) {
    f = produce(dir);
  }
}
