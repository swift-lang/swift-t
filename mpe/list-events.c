/*
 * list-events.c
 *
 *  Created on: Apr 25, 2012
 *      Author: wozniak
 *
 *  List all events defined in given log
 *  Prints in log order
 *  Pipe this through sort -n for sorted list
 */

#include <tools.h>

#include "mpe.c"

static void
usage()
{
  printf("usage: list-events.x <LOG>\n");
}

int
main(int argc, char* argv[])
{
  // SECTION I: SETUP

  initialize();

  if (argc != 2)
  {
    usage();
    exit(1);
  }
  //  The log we want to read
  char* input_log = argv[1];
  if (input_log == NULL ||
      strlen(input_log) == 0)
  {
    printf("Not given: input log (in clog format)\n");
    exit(1);
  }

  // SECTION II: READ DEFINITIONS

  bool success = read_defns(input_log);
  assert(success);

  // SECTION III: OUTPUT DEFINED EVENTS
  for (int i = 0; i < event_fill; i++)
  {
    int event = events[i];
    char* m;
    if (mode[event] == START)
      m = "START";
    else if (mode[event] == STOP)
      m = "STOP";
    else if (mode[event] == SOLO)
      m = "SOLO";
    else
      assert(false);
    char* name;
    bool found = table_ip_search(&IDs, event, (void**) &name);
    assert(found);
    printf("%5i -> %-5s %s\n", event, m, name);
  }

  return 0;
}
