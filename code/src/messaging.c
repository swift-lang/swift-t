
/*
 * messaging.c
 *
 *  Created on: Jul 20, 2012
 *      Author: wozniak
 */

#include "messaging.h"

/** Tag names: just for debugging */
static char* tag_names[MAX_TAGS];

static void add_tags(void);

static char* tag_prefix;
static int tag_prefix_length;

void
xlb_msg_init()
{
  memset(tag_names, '\0', MAX_TAGS*sizeof(char*));

  tag_prefix = "ADLB_TAG_";
  tag_prefix_length = strlen(tag_prefix);

  add_tags();
}

#define add_tag(tag) xlb_add_tag_name(tag, #tag)

void
add_tags()
{
  add_tag(ADLB_TAG_NULL);
  add_tag(ADLB_TAG_PUT);
  add_tag(ADLB_TAG_GET);
  add_tag(ADLB_TAG_STEAL);
  add_tag(ADLB_TAG_CREATE_HEADER);
  add_tag(ADLB_TAG_EXISTS);
  add_tag(ADLB_TAG_STORE_HEADER);
  add_tag(ADLB_TAG_RETRIEVE);
  add_tag(ADLB_TAG_ENUMERATE);
  add_tag(ADLB_TAG_CLOSE);
  add_tag(ADLB_TAG_SUBSCRIBE);
  add_tag(ADLB_TAG_SLOT_CREATE);
  add_tag(ADLB_TAG_SLOT_DROP);
  add_tag(ADLB_TAG_INSERT_HEADER);
  add_tag(ADLB_TAG_INSERT_ATOMIC);
  add_tag(ADLB_TAG_LOOKUP);
  add_tag(ADLB_TAG_UNIQUE);
  add_tag(ADLB_TAG_TYPEOF);
  add_tag(ADLB_TAG_CONTAINER_TYPEOF);
  add_tag(ADLB_TAG_CONTAINER_REFERENCE);
  add_tag(ADLB_TAG_CONTAINER_SIZE);
  add_tag(ADLB_TAG_LOCK);
  add_tag(ADLB_TAG_UNLOCK);
  add_tag(ADLB_TAG_CHECK_IDLE);
  add_tag(ADLB_TAG_SHUTDOWN);

  add_tag(ADLB_TAG_RESPONSE);
  add_tag(ADLB_TAG_RESPONSE_GET);
  add_tag(ADLB_TAG_RESPONSE_PUT);
  add_tag(ADLB_TAG_RESPONSE_STEAL);
  add_tag(ADLB_TAG_WORKUNIT);
  add_tag(ADLB_TAG_ABORT);
  add_tag(ADLB_TAG_WORK);
}

void
xlb_add_tag_name(int tag, char* name)
{
  // Chop off ADLB_TAG_ :
  if (! strncmp(name, tag_prefix, tag_prefix_length))
    name += tag_prefix_length;
  tag_names[tag] = strdup(name);
}

char*
xlb_get_tag_name(int tag)
{
  return tag_names[tag];
}

void
xlb_pack_work_unit(struct packed_put* p, work_unit* wu)
{
  p->answer = wu->answer;
  p->length = wu->length;
  p->priority = wu->priority;
  p->putter = wu->putter;
  p->target = wu->target;
  p->type = wu->type;
}
