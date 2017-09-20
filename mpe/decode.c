
/*
 * decode.c
 *
 *  Created on: Apr 25, 2012
 *      Author: wozniak
 *
 *  Decode MPE event IDs into human-readable output
 */

#include <tools.h>

#include "mpe.c"

static void
usage()
{
  printf("usage: decode.x <LOG> <RANK>\n");
}

bool process_line(const char* line);

/** Metadata events */
int et_metadata = -1;

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

  int e1, e2;
  bool b = event_name("metadata", &e1, &e2);
  if (b)
    et_metadata = e1;

  //  SECTION III: READ LOG
  char buffer[MAX_LINE];
  FILE* f = fopen(input_log, "r");
  line_number = 0;
  while (fgets(buffer, MAX_LINE, f) != NULL)
  {
    line_number++;
    chomp(buffer);
    bool b = process_line(buffer);
    if (!b) break;
  }
  fclose(f);

  return 0;
}

bool
process_line(const char* line)
{
  // Parse the log line, setting global variables
  bool b;
  b = parse_line(line);
  if (!b)
  {
    printf("parse error: line: %i: %s\n", line_number, line);
    exit(1);
  }

  // Check that this a normal event line
  if (! (type == EVENT_TYPE_BARE ||
         type == EVENT_TYPE_CAGO))
    return true;

  // Check that this is a relevant rank
  if (!(mpi_rank == ANY ||
        mpi_rank == rank))
    return true;

//  printf("ts: %0.6f et: %i\n", ts, et);

  // Mode string
  char* m;
  if (mode[et] == START)
    m = "START";
  else if (mode[et] == STOP)
    m = "STOP";
  else if (mode[et] == SOLO)
    m = "SOLO";
  else
  {
    printf("unknown event: %i\n", et);
    return false;
  }

  char* name;
  bool found = table_ip_search(&IDs, et, (void**) &name);
  assert(found);

  // Print difference
  // Time since last event
  // double d = ts - time_previous;
  // printf(" %0.5f\n", d);

  // Print event
  if (et == et_metadata)
  {
    char* metadata = get_string(line, "bytes");
    printf(" %0.5f %-5s %s %s\n", ts, m, name, metadata);
  }
  else
    printf(" %0.5f %-5s %s\n", ts, m, name);

  time_previous = ts;
  return true;
}
