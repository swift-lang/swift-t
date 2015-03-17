
/*
 * hostmap.c
 *
 *  Created on: Feb 4, 2015
 *      Author: wozniak
 */

#include <inttypes.h>
#include <stdio.h>
#include <sys/utsname.h>

#include <list_i.h>
#include <table.h>
#include <table_ip.h>
#include <tools.h>

#include "adlb.h"
#include "checks.h"
#include "common.h"
#include "debug.h"
#include "location.h"

typedef enum
{
  HOSTMAP_DISABLED,
  HOSTMAP_LEADERS,
  HOSTMAP_ENABLED
} hostmap_mode;

static hostmap_mode hostmap_mode_current;

/**
   Maps string hostname to list of int ranks which are running on
   that host
 */
static struct table hostmap;

/**
   Maps int rank to hostname.
   Only instantiated on servers.
   Only contains this server's ranks.
   The hostnames are pointers to keys in hostmap.
 */
static struct table_ip rankmap;

static void xlb_hostmap_free(void);

static void
report_ranks()
{
  int debug_ranks;
  getenv_integer("ADLB_DEBUG_RANKS", 0, &debug_ranks);
  if (!debug_ranks) return;

  struct utsname u;
  uname(&u);

  printf("ADLB_DEBUG_RANKS: rank: %i nodename: %s\n",
         xlb_comm_rank, u.nodename);
}

static bool get_hostmap_mode(void);
static void setup_leaders(int* leader_ranks, int leader_rank_count);
static void rankmap_add(bool am_server, int rank, const char* name);

bool
xlb_location_init(bool am_server)
{
  report_ranks();

  bool b = get_hostmap_mode();
  if (!b) return false;
  if (hostmap_mode_current == HOSTMAP_DISABLED)
  {
    adlb_leader_comm = MPI_COMM_NULL;
    return true;
  }

  struct utsname u;
  uname(&u);

  // Length of nodenames
  int length = (int) sizeof(u.nodename);

  // This may be too big for the stack
  char* allnames = malloc((size_t)(xlb_comm_size*length) * sizeof(char));

  char myname[length];
  // This prevents valgrind errors:
  memset(myname, 0, (size_t)length);
  strcpy(myname, u.nodename);

  int rc = MPI_Allgather(myname,   length, MPI_CHAR,
                         allnames, length, MPI_CHAR, adlb_comm);
  MPI_CHECK(rc);

  bool debug_hostmap = false;
  char* t = getenv("ADLB_DEBUG_HOSTMAP");
  if (t != NULL && strcmp(t, "1") == 0)
    debug_hostmap = true;

  int* leader_ranks = malloc((size_t)(xlb_comm_size) * sizeof(int));
  int leader_rank_count = 0;

  // Note: If hostmap mode is LEADERS, we free this table early
  table_init(&hostmap, 1024);

  if (am_server)
    table_ip_init(&rankmap, 128);

  char* p = allnames;
  for (int rank = 0; rank < xlb_comm_size; rank++)
  {
    char* name = p;

    if (xlb_comm_rank == 0 && debug_hostmap)
      printf("HOSTMAP: %s -> %i\n", name, rank);

    bool lowest_rank_on_node = !table_contains(&hostmap, name);

    if (lowest_rank_on_node && !xlb_is_server(rank))
    {
      leader_ranks[leader_rank_count++] = rank;
      TRACE("leader: %i\n", rank);
      if (rank == xlb_comm_rank)
      {
        xlb_am_leader = lowest_rank_on_node;
        DEBUG("am leader");
      }
    }

    // if (hostmap_mode_current != HOSTMAP_DISABLED) ??? -Justin 2015/2/4
    {
      if (lowest_rank_on_node)
      {
        struct list_i* L = list_i_create();
        table_add(&hostmap, name, L);
      }
      struct list_i* L;
      table_search(&hostmap, name, (void*) &L);
      list_i_add(L, rank);
    }

    rankmap_add(am_server, rank, name);

    p += length;
  }

  if (hostmap_mode_current == HOSTMAP_LEADERS)
    // We created this table just to set up leaders
    xlb_hostmap_free();

  setup_leaders(leader_ranks, leader_rank_count);

  free(leader_ranks);
  free(allnames);
  return true;
}

static void
rankmap_add(bool am_server, int rank, const char* name)
{
  if (xlb_map_to_server(rank) == xlb_comm_rank)
  {
    char* n = table_locate_key(&hostmap, name);
    table_ip_add(&rankmap, rank, n);
  }
}

static bool
get_hostmap_mode()
{
  // Deprecated feature:
  int disable_hostmap;
  bool b = getenv_integer("ADLB_DISABLE_HOSTMAP", 0, &disable_hostmap);
  if (!b)
  {
    printf("Bad integer in ADLB_DISABLE_HOSTMAP!\n");
    return false;
  }
  if (disable_hostmap == 1)
  {
    hostmap_mode_current = HOSTMAP_DISABLED;
    return true;
  }

  char* m = getenv("ADLB_HOSTMAP_MODE");
  if (m == NULL || strlen(m) == 0)
    m = "ENABLED";
  DEBUG("ADLB_HOSTMAP_MODE: %s\n", m);
  if (strcmp(m, "ENABLED") == 0)
    hostmap_mode_current = HOSTMAP_ENABLED;
  else if (strcmp(m, "LEADERS") == 0)
    hostmap_mode_current = HOSTMAP_LEADERS;
  else if (strcmp(m, "DISABLED") == 0)
    hostmap_mode_current = HOSTMAP_DISABLED;
  else
  {
    printf("Unknown setting: ADLB_HOSTMAP_MODE=%s\n", m);
    return false;
  }

  return true;
}

static void
setup_leaders(int* leader_ranks, int leader_rank_count)
{
  MPI_Group group_all, group_leaders;
  MPI_Comm_group(adlb_comm, &group_all);

  MPI_Group_incl(group_all, leader_rank_count, leader_ranks,
                 &group_leaders);
  MPI_Comm_create(adlb_comm, group_leaders, &adlb_leader_comm);
  MPI_Group_free(&group_leaders);
  MPI_Group_free(&group_all);
}

adlb_code
ADLB_Hostmap_stats(unsigned int* count, unsigned int* name_max)
{
  CHECK_MSG(hostmap_mode_current != HOSTMAP_DISABLED,
            "ADLB_Hostmap_stats: hostmap is disabled!");
  struct utsname u;
  *count = (uint)hostmap.size;
  *name_max = sizeof(u.nodename);
  return ADLB_SUCCESS;
}

adlb_code
ADLB_Hostmap_lookup(const char* name, int max,
                    int* output, int* actual)
{
  CHECK_MSG(hostmap_mode_current != HOSTMAP_DISABLED,
            "ADLB_Hostmap_lookup: hostmap is disabled!");
  struct list_i* L;
  bool b = table_search(&hostmap, name, (void*) &L);
  if (!b)
    return ADLB_NOTHING;
  int i = 0;
  for (struct list_i_item* item = L->head; item; item = item->next)
  {
    output[i++] = item->data;
    if (i == max)
      break;
  }
  *actual = i;
  return ADLB_SUCCESS;
}

adlb_code
ADLB_Hostmap_list(char* output, unsigned int max,
                  unsigned int offset, int* actual)
{
  CHECK_MSG(hostmap_mode_current != HOSTMAP_DISABLED,
            "ADLB_Hostmap_list: hostmap is disabled!");
  // Number of chars written
  int count = 0;
  // Number of hostnames written
  int h = 0;
  // Moving pointer into output
  char* p = output;
  // Counter for offset
  int j = 0;

  TABLE_FOREACH(&hostmap, item)
  {
    if (j++ >= offset)
    {
      int t = (int)strlen(item->key);
      if (count+t >= max)
        goto done;
      append(p, "%s", item->key);
      *p = '\r';
      p++;
      count += t;
      h++;
    }
  }

  done:
  *actual = h;
  return ADLB_SUCCESS;
}

const char *xlb_rankmap_lookup(int rank)
{
  void *host;
  if (table_ip_search(&rankmap, rank, &host))
  {
    return host;
  }
  return NULL;
}

static void
xlb_hostmap_free()
{
  if (hostmap_mode_current == HOSTMAP_DISABLED ||
      hostmap_mode_current == HOSTMAP_LEADERS) return;
  for (int i = 0; i < hostmap.capacity; i++)
  {
    table_entry *head = &hostmap.array[i];
    if (table_entry_valid(head))
    {
      table_entry *e, *next;
      bool is_head;

      for (e = head, is_head = true; e != NULL;
           e = next, is_head = false)
      {
        next = e->next; // get next pointer before freeing

        char* name = e->key;
        struct list_i* L = e->data;
        free(name);

        list_i_free(L);

        if (!is_head)
        {
          // Free unless inline in array
          free(e);
        }
      }
    }
  }
  table_release(&hostmap);
}

void
xlb_location_finalize()
{
  if (xlb_am_server)
    // The host names in this table are also in the hostmap table
    // and thus are already freed.
    table_ip_free_callback(&rankmap, false, NULL);
  xlb_hostmap_free();
  table_ip_clear(&rankmap);
}
