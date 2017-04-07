
/*
  Produce load level over time
  Load is number of processes in given state
  Given state is in MPE_EVENTS environment variable

  load <LOG> <MPI RANK>

  the log is the MPE-generated CLOG2 file
  the MPI rank is the rank to be analyzed
  If MPI rank is -1, analyze all ranks
*/

#include <stdio.h>

#include <tools.h>

#include "mpe.c"

/** Output time resolution (seconds) */
double timestep = 0.1;

/** Time at which to write next output line (seconds) */
double milestone = 0;

/** The start ID we are interested in */
int event_start = -1;

/** The stop ID we are interested in */
int event_stop = -1;

/** The current load: number of processes in the state of interest */
int load = 0;

/*
  Section I:   Basic definitions, setup
  Section II:  Populate IDs with known event type lines (type=sdef)
  Section III: Read real event lines (type=bare)
  Section IV:  Put events in buckets and emit loads

  Sections II & III are separate (two passes through log)
  because event IDs may occur before they are defined
*/

static void
usage()
{
  printf("usage: load.x <LOG> <MPI RANK>\n");
}

bool process_line(const char* line);


int main(int argc, char* argv[])
{
  // SECTION I: SETUP

  initialize();

  //  The log we want to read
  if (argc != 3)
  {
    usage();
    exit(1);
  }
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

  /** The state we are interested in */
  char* state_name = getenv("MPE_EVENTS");
  if (state_name == NULL || strlen(state_name) == 0)
  {
    printf("Not given: MPE_EVENTS!\n");
    exit(1);
  }

  // SECTION II: READ DEFINITIONS

  bool success = read_defns(input_log);
  assert(success);

  int e1, e2;
  event_name("ADLB_Finalize", &e1, &e2);
  ADLB_Finalize_ID = min_integer(e1,e2);

  // Look up the events
  event_name(state_name, &e1, &e2);
  event_start = min_integer(e1,e2);
  event_stop  = max_integer(e1,e2);
  // printf("events: %s: %i %i\n", state_name, e1, e2);

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
  if (type != EVENT_TYPE_BARE)
    return true;

  // Check that this is a relevant rank
  if (!(mpi_rank == ANY ||
        mpi_rank == rank))
    return true;

  if (et == ADLB_Finalize_ID)
    ADLB_Finalize_time = ts;

  /* check_msg(ts >= time_previous, */
  /*           "line: %i ts: %f time_previous: %f\n", */
  /*           line_number, ts, time_previous); */

  while (ts >= milestone)
  {
    printf("%0.5f %i\n", milestone, load);
    milestone += timestep;
  }

  //  Is this event what the user was looking for?
  if (et == event_start)
    load++;
  else if (et == event_stop)
    load--;

  time_previous = ts;
  return true;
}
