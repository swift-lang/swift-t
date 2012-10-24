
/*
  Reported to exm-user by Scott Krieder on 10/24/2012
  Cannot reproduce problem. -Justin
*/

#include <builtins.swift>
#include <io.swift>
#include <sys.swift>

main
{
     int bound = toint(argv("bound"));
     float sleepTime = tofloat(argv("sleeptime"));

     // print for debug
     printf("The number of arguments is: %i\n", argc());
     printf("The bound is: %i\n", bound);
     printf("The sleeptime is: %f\n", sleepTime);

     // run the sleep
     foreach i in [1:bound:1]{
               sleep(sleepTime);
     }
}
