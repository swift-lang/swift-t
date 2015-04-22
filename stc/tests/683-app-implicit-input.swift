import sys;

/*
 * Regression test for bug where implicit dependency between apps is
 * not respected
 */

@suppress=unused_output
app (file implicit) produce(string dir)
{
  "./683-produce.sh" dir
}

app (void o) consume(string dir, file implicit)
{
  "./683-consume.sh" dir
}

// Shouldn't execute until after signal set
app (void o) consume2(string dir, void signal)
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

  string dir2 = "tmp-683-void";
  void signal2;
  wait (sleep(0.1))
  {
    wait (produce(dir2))
    {
      // Set once file set
      signal2 = make_void();    
    }
  }
  consume2(dir2, signal2);
}
