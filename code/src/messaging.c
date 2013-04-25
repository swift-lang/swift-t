/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */


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
static size_t tag_prefix_length;

static void add_tags(void);

#endif

void
xlb_msg_init()
{
  // This is all just debugging
#ifndef NDEBUG
  tag_prefix = "ADLB_TAG_";
  tag_prefix_length = strlen(tag_prefix);
  memset(tag_names, '\0', XLB_MAX_TAGS*sizeof(char*));
  add_tags();
#endif
}

#ifndef NDEBUG

#define add_tag(tag) xlb_add_tag_name(tag, #tag)

void
add_tags()
{
  add_tag(ADLB_TAG_SYNC_REQUEST);
  add_tag(ADLB_TAG_PUT);
  add_tag(ADLB_TAG_RESPONSE_PUT);
  add_tag(ADLB_TAG_WORK);
  add_tag(ADLB_TAG_GET);
  add_tag(ADLB_TAG_RESPONSE_GET);
  add_tag(ADLB_TAG_IGET);
  add_tag(ADLB_TAG_CREATE_HEADER);
  add_tag(ADLB_TAG_MULTICREATE);
  add_tag(ADLB_TAG_EXISTS);
  add_tag(ADLB_TAG_STORE_HEADER);
  add_tag(ADLB_TAG_STORE_PAYLOAD);
  add_tag(ADLB_TAG_RETRIEVE);
  add_tag(ADLB_TAG_ENUMERATE);
  add_tag(ADLB_TAG_SUBSCRIBE);
  add_tag(ADLB_TAG_REFCOUNT_INCR);
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
  add_tag(ADLB_TAG_RESPONSE);
  add_tag(ADLB_TAG_SHUTDOWN_WORKER);
  add_tag(ADLB_TAG_SHUTDOWN_SERVER);
  add_tag(ADLB_TAG_FAIL);
}

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
