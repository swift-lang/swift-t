
/*
 * messaging.h
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 *
 *  XLB messaging conventions
 */

#ifndef MESSAGING_H
#define MESSAGING_H

#include "adlb.h"
#include "workqueue.h"

void xlb_msg_init(void);

void xlb_add_tag_name(int tag, char* name);

char* xlb_get_tag_name(int tag);

/*
   All of these client/handler functions (adlb.c/handlers.c)
   use messaging the same way:
   - They use communicator adlb_all_comm
   - They use the stack-allocated status or request object
   - They check the return code rc with MPI_CHECK() to return errors.
   Thus, we use these macros to make reading the logic easier
   This also allows us to wrap each call with TRACE_MPI()
        for easy message debugging
 */

#define SEND(data,length,type,rank,tag) { \
  TRACE_MPI("SEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int rc = MPI_Send(data,length,type,rank,tag,adlb_all_comm); \
  MPI_CHECK(rc); }

#define RSEND(data,length,type,rank,tag) { \
  TRACE_MPI("RSEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int rc = MPI_Rsend(data,length,type,rank,tag,adlb_all_comm); \
  MPI_CHECK(rc); }

#define SSEND(data,length,type,rank,tag) { \
  TRACE_MPI("SSEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int rc = MPI_Ssend(data,length,type,rank,tag,adlb_all_comm); \
  TRACE_MPI("SSENT"); \
  MPI_CHECK(rc); }

#define RECV(data,length,type,rank,tag) { \
  TRACE_MPI("RECV(from=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int rc = MPI_Recv(data,length,type,rank,tag, \
                    adlb_all_comm,&status); \
  TRACE_MPI("RECVD"); \
  MPI_CHECK(rc); }

#define IRECV(data,length,type,rank,tag) { \
  TRACE_MPI("IRECV(from=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int rc = MPI_Irecv(data,length,type,rank,tag, \
                     adlb_all_comm,&request); \
  MPI_CHECK(rc); }

#define WAIT(r,s) { \
  TRACE_MPI("WAIT"); \
  int rc = MPI_Wait(r,s); \
  MPI_CHECK(rc); \
  TRACE_MPI("WAITED"); }

/**
   Simple struct for message packing
 */
struct packed_put
{
  int type;
  int priority;
  int putter;
  int answer;
  int target;
  int length;
};

/**
   Simple struct for message packing
 */
struct packed_get
{
  int type;
  int target;
};

/**
   Simple struct for message packing
 */
struct packed_get_response
{
  adlb_code code;
  int length;
  int answer_rank;
  int type;
  /** From whom the payload will come (may be a redirect) */
  int payload_source;
};

/**
   Simple struct for message packing
 */
struct packed_id_type
{
  adlb_datum_id id;
  adlb_data_type type;
};

/**
   Simple struct for message packing
 */
struct packed_code_id
{
  adlb_data_code code;
  adlb_datum_id id;
};

/**
   Simple struct for message packing
 */
struct packed_code_length
{
  adlb_data_code code;
  int length;
};

/**
   Simple struct for message packing
 */
struct packed_enumerate
{
  adlb_datum_id id;
  char request_subscripts;
  char request_members;
  int count;
  int offset;
};

/** Member count of enum adlb_tag */
#define MAX_TAGS 128

/**
   ADLB message tags
   Some RPCs require two incoming messages: a header and a payload
*/
typedef enum
{
  ADLB_TAG_NULL = 0,

  /// tags incoming to server

  // task operations
  ADLB_TAG_PUT = 1,
  ADLB_TAG_GET,

  // data operations
  ADLB_TAG_CREATE_HEADER,
  ADLB_TAG_CREATE_PAYLOAD,
  ADLB_TAG_EXISTS,
  ADLB_TAG_STORE_HEADER,
  ADLB_TAG_STORE_PAYLOAD,
  ADLB_TAG_RETRIEVE,
  ADLB_TAG_ENUMERATE,
  ADLB_TAG_CLOSE,
  ADLB_TAG_SUBSCRIBE,
  ADLB_TAG_SLOT_CREATE,
  ADLB_TAG_SLOT_DROP,
  ADLB_TAG_INSERT_HEADER,
  ADLB_TAG_INSERT_PAYLOAD,
  ADLB_TAG_INSERT_ATOMIC,
  ADLB_TAG_LOOKUP,
  ADLB_TAG_UNIQUE,
  ADLB_TAG_TYPEOF,
  ADLB_TAG_CONTAINER_TYPEOF,
  ADLB_TAG_CONTAINER_REFERENCE,
  ADLB_TAG_CONTAINER_SIZE,
  ADLB_TAG_LOCK,
  ADLB_TAG_UNLOCK,
  ADLB_TAG_STEAL,
  ADLB_TAG_CHECK_IDLE,
  ADLB_TAG_SHUTDOWN,

  /// tags outgoing from server
  ADLB_TAG_RESPONSE,
  ADLB_TAG_RESPONSE_GET,
  ADLB_TAG_RESPONSE_PUT,
  ADLB_TAG_RESPONSE_STEAL,
  ADLB_TAG_WORKUNIT,
  ADLB_TAG_ABORT,

  /// tags that may be to/from server/worker
  /** Work unit payload */
  ADLB_TAG_WORK

} adlb_tag;


void xlb_pack_work_unit(struct packed_put* p, work_unit* wu);

#endif
