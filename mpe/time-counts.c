
/*

  Counts cumulative events completions over time

  Usage:
  time-counts.x <LOG> <MPI RANK>
  Environment must contain MPE_EVENTS

  the log is the MPE-generated CLOG2 file
  the MPI rank is the rank to be analyzed
  If MPI rank is -1, analyze all ranks

  Environment variable MPE_EVENTS contains the
  space-separated list of event stops that you want to count
  E.g.: export MPE_EVENTS="ADLB_Put ADLB_Get"
*/

#include <assert.h>

#include <tools.h>

#include "mpe.c"

// Settings:
// Bucket width in seconds
double bucket_width = 0.0001;

/*
  Section I:   Basic definitions, setup
  Section II:  Populate IDs with known event type lines (type=sdef)
  Section III: Read real event lines (type=bare)
  Section IV:  (none)
  Section V:   Output buckets

  Sections II & III are separate (two passes through log)
  because event IDs may occur before they are defined
*/

static void
usage()
{
  printf("usage: time-counts.x <LOG> <MPI RANK>\n");
}

/** Time of last call to ADLB_Finalize */
double finalize_time = 0;

/** Current completion */
int current = 0;

/** Count of completions */
int completions = 0;

#define MAX_COMPLETIONS (1024*1024)

struct completion
{
  double timestamp;
  int completions;
};

/**
  Map from index to [ list timestamp completions ]
 */
struct completion timeline[MAX_COMPLETIONS];

bool process_line(const char* line);

int
main(int argc, char*argv[])
{
  initialize();

  // SECTION I: SETUP
  memset(state_list, 0, MAX_EVENTS*sizeof(int));

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

  timeline[0].timestamp = 0;
  timeline[0].completions = 0;

  current++;

  //  SECTION II: READ DEFINITIONS
  bool success = read_defns(input_log);
  assert(success);

  int e1, e2;
  event_name("ADLB_Finalize", &e1, &e2);
  ADLB_Finalize_ID = min_integer(e1,e2);
  // printf("ADLB_Finalize_ID: %i\n", ADLB_Finalize_ID);

  state_list_init();

  //  SECTION III: READ EVENTS
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
  // SECTION V: OUTPUT
  for (int i = 0; i < current; i++)
  {
    printf("%0.6f %i\n",
           timeline[i].timestamp, timeline[i].completions);
  }
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

  // Is this a normal event line?
  if (type != EVENT_TYPE_BARE)
    return true;

  // Actual event line
  if (mpi_rank == ANY ||
      mpi_rank == rank)
  {
    if (et == ADLB_Finalize_ID)
      ADLB_Finalize_time = ts;

    //  Is this event name in the state_list?
    if (!state_list_contains(et))
      return true;

//    printf("line: %i ts: %f time_previous: %f event: %i\n",
//           line_number, ts, time_previous, et);

    check_msg(ts >= time_previous,
              "line: %i ts: %f time_previous: %f\n",
              line_number, ts, time_previous);

    int m = mode[et]; // [ dict get $mode $event_type ]
    if (m != STOP) return true;
    completions++;
    timeline[current].timestamp = ts;
    timeline[current].completions = completions;
    time_previous = ts;

    current++;
  }
  return true;
}

