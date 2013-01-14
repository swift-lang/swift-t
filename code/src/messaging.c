
/*
 * messaging.c
 *
 *  Created on: Jul 20, 2012
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <stdio.h>

#include "tools.h"

#include "messaging.h"

#ifndef NDEBUG

/** Tag names: just for debugging */
static char* tag_names[XLB_MAX_TAGS];

static char* tag_prefix;
static int tag_prefix_length;

#endif

void
xlb_msg_init()
{
  // This is all just debugging
#ifndef NDEBUG
  tag_prefix = "ADLB_TAG_";
  tag_prefix_length = strlen(tag_prefix);
  memset(tag_names, '\0', XLB_MAX_TAGS*sizeof(char*));
#endif
}

#ifndef NDEBUG
void
xlb_add_tag_name(int tag, char* name)
{
  // Chop off ADLB_TAG_ :
  if (! strncmp(name, tag_prefix, tag_prefix_length))
    name += tag_prefix_length;
  char* t;
  int count = asprintf(&t, "%s(%i)", name, tag);
  ASSERT(count != -1);
  tag_names[tag] = t; // strdup(name);
}

char*
xlb_get_tag_name(int tag)
{
  return tag_names[tag];
}
#endif
