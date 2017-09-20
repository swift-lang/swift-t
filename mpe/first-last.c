
/*
 * first-last.c
 *
 *  Created on: Apr 26, 2012
 *      Author: wozniak
 *
 *  Report times of first and last events in MPE_EVENTS
 */

#include <tools.h>

#include "mpe.c"

static void
usage()
{
  printf("usage: first-last.x <LOG> <RANK>\n");
}

bool process_line(const char* line);

/** Have we found a start event yet? */
static bool found_start = false;

/** First start event timestamp */
static double first_start_timestamp = -1;

/** Last end event timestamp */
static double last_end_timestamp = -1;

/** Last end event type */
static event_type last_end_type = EVENT_TYPE_NONE;

int
main(int argc, char* argv[])
{
  // SECTION I: SETUP

  initialize();

  if (argc != 3)
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

  int n = sscanf(argv[2], "%i", &mpi_rank);
  if (n != 1)
  {
    printf("Not given: MPI rank!\n");
    exit(1);
  }

  // SECTION II: READ DEFINITIONS
  bool success = read_defns(input_log);
  assert(success);

  state_list_init();

  //  SECTION III: READ LOG
  char buffer[MAX_LINE];
  FILE* f = fopen(input_log, "r");
  line_number = 0;
  while (fgets(buffer, MAX_LINE, f) != NULL)
  {
    line_number++;
    chomp(buffer);
    process_line(buffer);
  }
  fclose(f);

  // Report last event from MPE_EVENTS
  check_msg(last_end_type != EVENT_TYPE_NONE, "no events found!");
  char* name;
  bool found = table_ip_search(&IDs, last_end_type, (void**) &name);
  check_msg(found,
            "unknown last event type: %i\n", last_end_type);

  printf("last:  %9.3f %i %s\n",
           last_end_timestamp, last_end_type, name);

  double difference = last_end_timestamp - first_start_timestamp;
  printf("difference: %9.3f\n", difference);

  return 0;
}

bool
process_line(const char* line)
{
  // Parse the log line, setting global variables
  bool b = parse_line(line);
  if (!b)
  {
    printf("parse error: line: %i: %s\n", line_number, line);
    exit(1);
  }

  // Check that this a normal event line
  if (type != EVENT_TYPE_BARE)
    return true;

  // Check that this is a relevant rank
  if (!(mpi_rank == ANY ||
        mpi_rank == rank))
    return true;

  // Is this event name in the state_list?
  if (!state_list_contains(et))
    return true;

  assert(ts >= time_previous);
  time_previous = ts;

  if (mode[et] == START)
  {
    if (found_start)
    {
      return true;
    }
    else
    {
      // Found first start event from MPE_EVENTS
      found_start = true;
      first_start_timestamp = ts;
      char* name;
      bool found = table_ip_search(&IDs, et, (void**) &name);
      valgrind_assert(found);
      printf("first: %9.3f %i %s\n", ts, et, name);
    }
  }

  // We don't really know the last end event until EOF
  last_end_timestamp = ts;
  last_end_type = et;
  return true;
}
