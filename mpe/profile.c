
/*
 * profile.c
 *
 *  Created on: Apr 25, 2012
 *      Author: wozniak
 *
 *  Compute fraction of time spent in each state
 */

#include <stdio.h>

#include <tools.h>

#include "mpe.c"

/*
  Section I:   Basic definitions, setup
  Section II:  Populate IDs with known event type lines (type=sdef)
  Section III: Read real event lines (type=bare)
  Section IV:  Output:
               Put statistics on stdout
               For each MPE state name in MPE_EVENTS, produce
               state.<name>.data, a list of all times spent in that
               state.*.data are placed in PWD

  Sections II & III are separate (two passes through log)
  because event IDs may occur before they are defined

  If MPE_CUTOFF is set to a floating point value, this program will
  exit after the first event timestamp that exceeds that value

  Nested states are treated as a single state
*/

static void
usage()
{
  printf("usage: profile.x <LOG> <MPI RANK>\n");
}

bool process_line(const char* line);
void cutoff(void);

/**
   state[event e][rank r]
   is the last time rank r started event e
   if 0, the state is not current
   Actual event IDs are normalized so that the lowest actual event
   ID in the log maps to event 0 in the state array (cf. EVENT())
 */
double* state = NULL;

/**
   nest[event e][rank r]
   is the current nesting level of event e
   If 0, the state is not current
   Maximum nesting is UINT8_MAX==255
   Actual event IDs are normalized so that the lowest actual event
   ID in the log maps to event 0 in the state array (cf. EVENT())
 */
uint8_t* nest = NULL;

/** Difference between smallest event ID and biggest event ID */
int event_range;

/** Difference between actual event ID and index into state array */
int event_offset = -1;

/** Map event ID into event_range for indexing in state array */
#define EVENT(x) (x-event_offset)

/** Map state array index back to event ID (event-Reverse) */
#define EVENTR(x) (x+event_offset)

/** Total time spent in this state */
double profile[MAX_EVENTS];

/** If true, report individual state times in output files */
bool report_state_times = true;

/**
   report_call_times output files
   We have one output file for each start event in state_list
   NULL-terminated list
*/
FILE* state_list_fds[MAX_EVENTS];

// Features for report_state_times
void setup_state_list(void);
void report_state_time(int et, double duration);
void close_state_list_fds();

double mpe_cutoff = -1;

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

  // The log we want to read
  char* input_log = argv[1];
  if (input_log == NULL ||
      strlen(input_log) == 0)
  {
    printf("Not given: input log (in clog format)\n");
    exit(1);
  }

  // The MPI rank of interest
  int n = sscanf(argv[2], "%i", &mpi_rank);
  if (n != 1)
  {
    printf("Not given: MPI rank!\n");
    exit(1);
  }

  mpe_cutoff = mpe_cutoff_get();

  for (int i = 0; i < MAX_EVENTS; i++)
    profile[i] = 0;

  // SECTION II: READ DEFINITIONS
  bool success = read_defns(input_log);
  assert(success);

  int e1, e2;
  bool b = event_name("ADLB_all_finalize", &e1, &e2);
  valgrind_assert_msg(b, "ADLB_all_finalize not found in log!");
  ADLB_Finalize_ID = min_integer(e1,e2);

  int m1 = event_min();
  int m2 = event_max();
  // Number of events in range
  event_range = m2 - m1;

  // printf("event_range: %i\n", event_range);
  // printf("world_size: %i\n", world_size);

  event_offset = m1;
  state = malloc(event_range * world_size * sizeof(double));
  valgrind_assert(state);
  nest = malloc(event_range * world_size * sizeof(uint8_t));
  valgrind_assert(nest);

  for (int e = 0; e < event_range; e++)
    for (int r = 0; r < world_size; r++)
    {
      state[e*world_size+r] = 0;
      nest[e*world_size+r] = 0;
    }

  if (report_state_times)
    setup_state_list();

  //  SECTION III: READ LOG
  char buffer[MAX_LINE];
  FILE* f = fopen(input_log, "r");
  line_number = 0;
  while (fgets(buffer, MAX_LINE, f) != NULL)
  {
    line_number++;
    chomp(buffer);
    process_line(buffer);
    if (mpe_cutoff != -1 && ts > mpe_cutoff)
      break;
  }
  fclose(f);

  // SECTION IV: OUTPUT
  printf("%-22s %-7s %-13s  %-9s\n",
         "Event", "Calls", "Total (s)", "Avg (s)");

  for (int i = 0; i < event_fill; i++)
  {
    int event = events[i];
    double p  = profile[event];
    int    c  = calls[event];

    if (mode[event] == STOP)
      continue;
    char* name;
    bool found = table_ip_search(&IDs, event, (void**) &name);
    assert(found);
    double avg = (c != 0) ? p/c : 0;
    printf("%-22s %7i %13.3f  %9.6f\n",
             name, c,  p,     avg);
  }

  if (report_state_times)
    close_state_list_fds();

  mpe_finalize();

  return 0;
}

void
setup_state_list()
{
  state_list_init();
  // Index into state_list_fds
  int index = 0;
  for (int i = 0; i < state_list_size; i += 2)
  {
    char* name;
    bool found = table_ip_search(&IDs, state_list[i], (void**) &name);
    valgrind_assert(found);
    char filename[128];
    sprintf(filename, "state.%s.data", name);
    FILE* fd = fopen(filename, "w");
    if (fd == NULL)
    {
      printf("Could not write to: %s\n", filename);
      exit(1);
    }
    state_list_fds[index++] = fd;
  }
  // Last entry is NULL
  state_list_fds[index] = NULL;
}

void
close_state_list_fds()
{
  for (int i = 0; state_list_fds[i]; i++)
    fclose(state_list_fds[i]);
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

  if (mpe_cutoff != -1 && ts > mpe_cutoff)
  {
    cutoff();
    return true;
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

  int e = EVENT(et);

  // printf("ts: %10.5f et: %i e: %i rank: %i\n", ts, et, e, rank);

  if (mode[et] == START)
  {
    // Increment call counter for this event
    calls[et]++;
    // Ensure this event is not already running
    if (nest[e*world_size+rank] == UINT8_MAX)
      valgrind_fail("nesting for event: %i on rank: %i exceeds: %i",
                    e, rank, UINT8_MAX);
    // If this is the outermost level, set start time
    if (nest[e*world_size+rank] == 0)
      state[e*world_size+rank] = ts;
    // Increment nesting level
    nest[e*world_size+rank]++;
  }
  else if (mode[et] == STOP)
  {
    // Decrement event numbers: STOP IDs are 1 greater than STARTs
    e--;
    et--;
    if (nest[e*world_size+rank] == 0)
      valgrind_fail("nesting for event: %i on rank: %i is negative!");
    // Decrement nesting level
    int n = --nest[e*world_size+rank];
    // If nesting is back at zero, report result
    if (n == 0)
    {
      // Lookup state start time
      double t = state[e*world_size+rank];
      // Calculate time spent in state
      double duration = ts - t;
      // Increment profile time by time spent in state
      profile[et] += duration;
      // Output
      if (report_state_times)
        report_state_time(et, duration);
      // Reset start time
      state[e*world_size+rank] = 0;
    }
  }
  else if (mode[et] == SOLO)
  {
    // Increment call counter for this event
    calls[et]++;
  }

  time_previous = ts;
  return true;
}

/**
   Cut off all running states
 */
void
cutoff()
{
  for (int e = 0; e < event_range; e++)
    for (int r = 0; r < world_size; r++)
    {
      double t = state[e*world_size+r];
      if (t != 0)
      {
        int r = EVENTR(e);
        double duration = ts - t;
        profile[r] += duration;
      }
    }
}

void
report_state_time(int et, double duration)
{
  int index = state_list_index(et);
  if (index == -1)
    // The user did not ask for this event in MPE_EVENTS
    return;
  // Offset into state_list_fds
  index /= 2;
  // printf("et: %i index: %i\n", et, index);
  FILE* fd = state_list_fds[index];
  fprintf(fd, "%0.6f\n", duration);
}
