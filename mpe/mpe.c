
/*
  MPE.C
  Reusable MPE log processing functions
  Simply include this whole file
*/

#include <assert.h>
#include <errno.h>
#include <limits.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include <exm-string.h>
#include <list_ip.h>
#include <table_ip.h>
#include <table_lp.h>

/** Line number in the clog_txt */
int line_number = 0;

const int ANY = -1;

/** The rank we want to profile - may be (-1=ANY) */
int mpi_rank = -1;

int world_size = -1;

/** The event ID for ADLB_Finalize */
int ADLB_Finalize_ID = -1;

/** The last time at which ADLB_Finalize was called */
double ADLB_Finalize_time = -1;

/** Map from start or stop event IDs to known MPE event names */
struct table_ip IDs;

/** Copy of environment variable MPE_EVENTS as split by strtok */
char* mpe_events;

/**
   event start/stop
*/
typedef enum
{
  EVENT_SS_UNKNOWN=0, START, STOP, SOLO
} event_ss;

/**
   Event type
 */
typedef enum
{
  EVENT_TYPE_UNKNOWN,
  EVENT_TYPE_NONE,
  /** State definition */
  EVENT_TYPE_SDEF,
  /** Event definition */
  EVENT_TYPE_EDEF,
  /** Bare event */
  EVENT_TYPE_BARE,
  /** Event with cargo bytes attached */
  EVENT_TYPE_CAGO
} event_type;

/** Maximal line length in clog_txt */
const int MAX_LINE = 10*1024;

/** Maximal number of event types */
#define MAX_EVENTS 10*1024

/** Map from start, stop event IDs to START or STOP */
int8_t mode[MAX_EVENTS];

/** List of all defined event IDs */
int events[MAX_EVENTS];

/** Index into events: for use when filling
    After initialization, is count of event types */
int event_fill = 0;

/** Map from start event type to count of calls to that state */
int calls[MAX_EVENTS];

/** Current value: timestamp */
double ts;

/** Current value: event type */
event_type type = EVENT_TYPE_UNKNOWN;

/** Current value: MPI rank */
int rank;

/** Current value: event ID */
int et;

/** Time of previous event in log (seconds) */
double time_previous = 0;

bool process_sdef(const char* line);
bool process_edef(const char* line);

void
initialize()
{
  table_ip_init(&IDs, MAX_EVENTS);
  memset(mode, 0, MAX_EVENTS*sizeof(int8_t));
  memset(calls, 0, MAX_EVENTS*sizeof(int));
  memset(events, 0, MAX_EVENTS*sizeof(int));

  // Sometimes the first timestamp is <0 - allow user to correct for this
  bool b = getenv_double("MPE_TIME_PREVIOUS", 0, &time_previous);
  if (b) printf("MPE_TIME_PREVIOUS: %.3f\n", time_previous);
}

static inline char*
get_value(const char* s)
{
  char* t = strchr(s, '=');
  return t+1;
}

static inline char*
get_string(const char* s, const char* key)
{
  char* p = strstr(s, key);
  if (!p)
    return NULL;
  char* t = get_value(p);
  return t;
}

static inline bool
get_integer(const char* s, const char* key, int* result)
{
  char* t = get_string(s, key);
  if (!t)
  {
    printf("get_integer: could not find: %s\n in: %s\n", key, s);
    return false;
  }

  long r = strtol(t, NULL, 10);
  *result = r;
  return true;
}


static inline double
get_double(const char* s, const char* key)
{
  char* t = get_string(s, key);
  if (!t)
  {
    printf("get_float: could not find: %s\n", key);
    exit(1);
  }
  errno = 0;
  double r = strtod(t, NULL);
  if (errno)
  {
    printf("get_float: could not parse (strtod): %s\n", key);
    exit(1);
  }
  return r;
}

static inline void
set_timestamp(const char* line)
{
  ts = get_double(line, "ts");
}

static inline void
set_type(const char* line)
{
  char* p = get_string(line, "type");
  if (p == NULL)
    type = EVENT_TYPE_NONE;
  else if (!strncmp(p, "bare", 4))
    type = EVENT_TYPE_BARE;
  else if (!strncmp(p, "cago", 4))
    type = EVENT_TYPE_CAGO;
  else if (!strncmp(p, "sdef", 4))
    type = EVENT_TYPE_SDEF;
  else if (!strncmp(p, "edef", 4))
    type = EVENT_TYPE_EDEF;
  else
    type = EVENT_TYPE_UNKNOWN;
}

static inline void
set_mpi_rank(const char* line)
{
  bool b = get_integer(line, "rank", &rank);
  assert(b);
}

static inline bool
set_event_id(const char* line)
{
  bool b = get_integer(line, "et", &et);
  return b;
}

/**
   Updates several globals wrt the current line
 */
bool
parse_line(const char* line)
{
  bool b;
  set_type(line);
  if (!(type == EVENT_TYPE_BARE ||
        type == EVENT_TYPE_CAGO))
    return true;
  set_timestamp(line);
  set_mpi_rank(line);
  b = set_event_id(line);
  if (!b) return false;

  return true;
}

bool
process_defn(const char* line)
{
  bool success;

  // Does this line define world size?
  if (strstr(line, "max_comm_world_size"))
  {
    success = get_integer(line, "max_comm_world_size", &world_size);
    assert(success);
    return success;
  }

  // Is this a normal event line?
  if (! strstr(line, "ts="))
    return true;

  // Is this an sdef or edef?
  if (strstr(line, "type=sdef"))
    success = process_sdef(line);
  else if (strstr(line, "type=edef"))
    success = process_edef(line);
  else
    // Not a defn - ignore it
    success = true;

  return success;
}

bool
process_sdef(const char* line)
{
  char* name = get_string(line, "name");
  name = string_dup_word(name);
  int s_et, e_et;
  bool b;
  b = get_integer(line, "s_et", &s_et);
  assert(b);
  b = get_integer(line, "e_et", &e_et);
  assert(b);

  // Start event type
  b = table_ip_add(&IDs, s_et, name);
  if (!b)
    return true;
  mode[s_et] = START;
  // End event type
  b = table_ip_add(&IDs, e_et, name);
  if (!b)
    printf("error adding event: %s\n", name);
  assert(b);
  // printf("%i %i -> %s\n", s_et, e_et, name);
  mode[e_et] = STOP;

  events[event_fill++] = s_et;
  events[event_fill++] = e_et;

  return true;
}

bool
process_edef(const char* line)
{
  char* name = get_string(line, "name");
  name = string_dup_word(name);
  int e;
  bool b;
  b = get_integer(line, "et", &e);
  assert(b);

  // Start event type
  b = table_ip_add(&IDs, e, name);
  if (!b)
    return true;
  mode[e] = SOLO;

  events[event_fill++] = e;

  return true;
}


/**
   List the space-separated strings in MPE_EVENTS
   Result is null-terminated
   Caller must free result but not the strings it points to
          (which are the copies of the system's environment strings
           in mpe_events)
 */
bool
mpe_events_list(char*** result)
{
  char* t = getenv("MPE_EVENTS");

  // printf("MPE_EVENTS: %p\n", t);

  // Make a copy for strtok
  if (t == NULL)
    mpe_events = strdup("");
  else
    mpe_events = strdup(t);

  char** r = malloc(MAX_EVENTS*sizeof(char*));
  // Assign return value:
  *result = r;

  char* p = mpe_events;
  int i = 0;
  while (true)
  {
    char* token = strtok(p, " ");
    if (!token)
      break;
    r[i++] = token;
    p = NULL;
  }
  // NULL-terminate result
  r[i] = NULL;

  return true;
}

/**
   Given an event name, return the start/end event IDs
   May be returned in any order
   This is not a fast lookup
 */
bool
event_name(const char* name, int* e1, int* e2)
{
  int count = 0;
  TABLE_IP_FOREACH(&IDs, item)
  {
    if (strcmp(name, item->data) == 0)
    {
      if (count == 0)
        *e1 = item->key;
      else if (count == 1)
        *e2 = item->key;
      else
      {
        printf("event_name(): found more than two events for: %s\n",
               name);
        return false;
      }
      count++;
    }
  }
  if (count == 0)
  {
    printf("event_name(): not found: %s\n", name);
    return false;
  }
  return true;
}

/**
   The states we are interested in, based on MPE_EVENTS
   Not always used
*/
int state_list[MAX_EVENTS];

/** Number of entries in state_list */
int state_list_size;

/**
   Initialize state_list
 */
void
state_list_init()
{
  char** r;
  bool b = mpe_events_list(&r);
  assert(b);

  // Index into state_list:
  int j = 0;
  // Index into r:
  int i = 0;
  for (i = 0; r[i] != NULL; i++)
  {
    // printf("state_list: %i = %s\n", j, r[i]);
    int e1 = 0, e2 = 0;
    event_name(r[i], &e1, &e2);
    int e_start = min_integer(e1,e2);
    int e_stop  = max_integer(e1,e2);
    // printf("state_list[%i] = %s (%i %i)\n",
    //           i, r[i], e_start, e_stop);
    state_list[j++] = e_start;
    state_list[j++] = e_stop;
    state_list_size += 2;
  }
  free(r);
}

/**
   Does state_list contain given event ID?
 */
bool
state_list_contains(int event)
{
  for (int i = 0; i < MAX_EVENTS; i++)
    if (state_list[i] == event)
      return true;
    else if (state_list[i] == 0)
      return false;
  return false;
}

/**
   Index of given event in state_list
 */
int
state_list_index(int event)
{
  for (int i = 0; i < MAX_EVENTS; i++)
    if (state_list[i] == event)
      return i;
    else if (state_list[i] == 0)
      return -1;
  return -1;
}

static void
file_format_error(void)
{
  printf("ERROR: This is not a valid CLOG2.TXT file\n");
  exit(EXIT_FAILURE);
}

bool
read_defns(const char* clog_txt)
{
  char buffer[MAX_LINE];

  FILE* f = fopen(clog_txt, "r");
  if (!f)
  {
    printf("could not open: %s\n", clog_txt);
    exit(1);
  }

  // Ensure this is a CLOG2.TXT file
  fread(buffer, sizeof(char), 16, f);
  if (strncmp(buffer, "CLOG", 4))
    file_format_error();
  for (int i = 0; i < 16; i++)
    if (buffer[i] == '\0')
      file_format_error();

  rewind(f);
  line_number = 0;
  while (fgets(buffer, MAX_LINE, f) != NULL)
  {
    line_number++;
    chomp(buffer);
    bool success = process_defn(buffer);
    if (! success)
    {
      printf("error in line: %i\n", line_number);
      exit(1);
    }
  }
  fclose(f);
  return true;
}

/**
   Return minimal event ID
 */
int
event_min()
{
  int m = INT_MAX;
  for (int i = 0; i < MAX_EVENTS; i++)
    if (events[i] > 0 && events[i] < m)
      m = events[i];
  return m;
}

/**
   Return maximal event ID
 */
int
event_max()
{
  int m = 1;
  for (int i = 0; i < MAX_EVENTS; i++)
    if (events[i] > m)
      m = events[i];
  return m;
}

/**
   Return MPE_CUTOFF: conventionally the timestamp at which to end
   log processing early
   Returns -1 if not set
*/
double
mpe_cutoff_get()
{
  char* s = getenv("MPE_CUTOFF");
  if (s == NULL)
    return -1;
  float f;
  int n = sscanf(s, "%f", &f);
  if (n != 1)
  {
    printf("MPE_CUTOFF is not a floating-point number: %s\n", s);
    exit(1);
  }
  double result = f;
  return result;
}

void
mpe_finalize(void)
{
  free(mpe_events);
}
