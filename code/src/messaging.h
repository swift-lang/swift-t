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
 * messaging.h
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 *
 *  XLB messaging conventions
 */

#ifndef MESSAGING_H
#define MESSAGING_H

#include <limits.h>

#include "adlb.h"
#include "checks.h"
#include "debug.h"
#include "workqueue.h"

/*
 * Pragma to disable padding in structures:
 * a) to reduce message sizes
 * b) to avoid confusing tools like valgrind that detect uninitialized bytes
 *    being sent.
 */
#pragma pack(push, 1)

/**
   Initialize the messaging functionality
 */
void xlb_msg_init(void);

/**
   Finalize the messaging functionality
 */
void xlb_msg_finalize(void);

/**
   Set a string name for debugging MPI message tags
 */
void xlb_add_tag_name(int tag, char* name);

/**
   Lookup string name for debugging MPI message tags
 */
const char* xlb_get_tag_name(int tag);

/*
   All of these client/handler functions (adlb.c,handlers.c,etc.)
   use messaging the same way:
   - They use communicator adlb_comm
   - They use the stack-allocated status or request object
   - They check the return code rc with MPI_CHECK() to return errors.
   Thus, we use these macros to ease reading the message protocols
   This also allows us to wrap each call with TRACE_MPI()
        for message debugging
   Note that the _rc (return code) used here is in a nested scope
 */

#define SEND(data,length,type,rank,tag) { \
  TRACE_MPI("SEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Send(data,length,type,rank,tag,adlb_comm); \
  MPI_CHECK(_rc); }

#define RSEND(data,length,type,rank,tag) { \
  TRACE_MPI("RSEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Rsend(data,length,type,rank,tag,adlb_comm); \
  MPI_CHECK(_rc); }

#define SSEND(data,length,type,rank,tag) { \
  TRACE_MPI("SSEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Ssend(data,length,type,rank,tag,adlb_comm); \
  TRACE_MPI("SSENT"); \
  MPI_CHECK(_rc); }

#define ISEND(data,length,type,rank,tag,req) { \
  TRACE_MPI("ISEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Isend(data,length,type,rank,tag,adlb_comm,req); \
  MPI_CHECK(_rc); }

#define IRSEND(data,length,type,rank,tag,req) { \
  TRACE_MPI("IRSEND(to=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Irsend(data,length,type,rank,tag,adlb_comm,req); \
  MPI_CHECK(_rc); }

#define RECV(data,length,type,rank,tag) \
        RECV_STATUS(data,length,type,rank,tag,&status)

#define RECV_STATUS(data,length,type,rank,tag,status_ptr) { \
  TRACE_MPI("RECV(from=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Recv(data,length,type,rank,tag, \
                    adlb_comm,status_ptr); \
  TRACE_MPI("RECVD"); \
  MPI_CHECK(_rc); }

#define IRECV(data,length,type,rank,tag) \
      IRECV2(data, length, type, rank, tag, &request);

#define IRECV2(data,length,type,rank,tag,req) { \
  TRACE_MPI("IRECV(from=%i,tag=%s)", rank, xlb_get_tag_name(tag)); \
  int _rc = MPI_Irecv(data,length,type,rank,tag, \
                     adlb_comm,req); \
  MPI_CHECK(_rc); }

// We don't TRACE this
#define IPROBE(target,tag,flag,status) { \
    int _rc = MPI_Iprobe(target,tag,adlb_comm,flag,status); \
    MPI_CHECK(_rc); }

#define WAIT(r,s) { \
  TRACE_MPI("WAIT"); \
  int _rc = MPI_Wait(r,s); \
  MPI_CHECK(_rc); \
  TRACE_MPI("WAITED"); }

// MPI_Test, ignoring status
#define MPI_TEST(r, flag) { \
  TRACE_MPI("TEST"); \
  int _rc = MPI_Test(r, flag, MPI_STATUS_IGNORE); \
  MPI_CHECK(_rc);}

#define MPI_TEST2(r, flag, status) { \
  TRACE_MPI("TEST"); \
  int _rc = MPI_Test(r, flag, status); \
  MPI_CHECK(_rc);}
/** Simplify cases when only a tag is sent */
#define SEND_TAG(rank,tag) SEND(NULL,0,MPI_BYTE,rank,tag)

/** Simplify cases when only a tag is recvd */
#define RECV_TAG(rank,tag) RECV(NULL,0,MPI_BYTE,rank,tag)


#define CANCEL(r) { \
  TRACE_MPI("CANCEL"); \
  int _rc = MPI_Cancel(r); \
  MPI_CHECK(_rc); }

/** MPI data type tags */
// 64-bit int
#define MPI_ADLB_ID MPI_LONG_LONG

// Macros to help with packing and unpacking variables
// TODO: error handling in case buffer too small

#define MSG_PACK_BIN(buf_pos, var)        \
  {                                       \
    memcpy(buf_pos, &var, sizeof(var));   \
    buf_pos += sizeof(var);               \
  }

#define MSG_UNPACK_BIN(buf_pos, var)      \
  {                                       \
    memcpy(var, buf_pos, sizeof(*var));   \
    buf_pos += sizeof(*var);               \
  }


// Maximum number of bytes to append to put request.
// This is an optimization to allow sending small tasks
// without an extra round-trip
#define PUT_INLINE_DATA_MAX 512

/**
   Put request
 */
struct packed_put
{
  int type;
  int priority;
  int putter;
  int answer;
  int target;
  int length;
  int parallelism;
  bool has_inline_data;
  char inline_data[]; /* Put small tasks here */
};

#define PACKED_PUT_SIZE(inline_data_len) \
        (sizeof(struct packed_put) + inline_data_len)

#define PACKED_PUT_MAX (PACKED_PUT_SIZE(PUT_INLINE_DATA_MAX))

/**
   Put request with data dependencies
 */
struct packed_put_rule
{
  int type;
  int priority;
  int putter;
  int answer;
  int target;
  int length;
  int parallelism;
  int id_count;
  int id_sub_count;
#ifndef NDEBUG
  int name_strlen;
#endif
  bool has_inline_data;
  /* Pack ids/subscripts and small tasks here.
     Format is:
     1. Array of ids with length id_count
        Use type adlb_datum_id to get correct alignment for first array
     2. id_sub_count packed ids/subscripts 
     3. Name, unless NDEBUG enabled, packed w/o null terminator
     4. Inline task, if has_inline_data is true
   */
  adlb_datum_id inline_data[]; 
};

/**
  Struct with notification counts for embedding in other structure
 */
struct packed_notif_counts
{
  int notify_count;
  int reference_count;
  int refc_count;
  int extra_data_count;
  int extra_data_bytes;
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
  /** Parallelism: 1=normal single process task, >1=parallel task */
  int parallelism;
};

struct packed_create_response
{
  adlb_data_code dc;
  /** id of created item */
  adlb_datum_id id;
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
struct retrieve_response_hdr
{
  struct packed_notif_counts notifs;
  adlb_data_code code;
  adlb_data_type type;
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
  adlb_refc decr;
};

struct packed_enumerate_result
{
  adlb_data_code dc;
  int records; // Count of elements returned
  int length; // length of data in bytes
  adlb_data_type key_type;
  adlb_data_type val_type;
};

struct packed_notif
{
  adlb_datum_id id;
  int subscript_data; // index of extra data item, -1 for no subscript
  int rank; // Rank to notify
};

struct packed_reference
{
  adlb_refc refcounts; // Refcounts transferred
  adlb_datum_id id; // ID to set
  int subscript_data; // index of extra data subscript
  adlb_data_type type;
  int val_data; // index of extra data item
};

/**
 * Request refcount info
 */
struct packed_refcounts_req {
  adlb_datum_id id;
  adlb_refc decr;
};

struct packed_refcounts_resp {
  adlb_data_code dc;
  adlb_refc refcounts;
};

/**
   Count increment or decrement
 */
struct packed_incr
{
  adlb_datum_id id;
  adlb_refc change;
};

/**
   Response to reference count operation
 */
struct packed_incr_resp
{
  bool success;
  struct packed_notif_counts notifs;
};

/**
 * Header for store message
 */
struct packed_store_hdr
{
  adlb_datum_id id;
  adlb_data_type type; // Type of data
  adlb_refc refcount_decr;
  adlb_refc store_refcounts; // Refcounts to store
  int subscript_len; // including null byte, 0 if no subscript
};


/**
 * Response for store 
 */
struct packed_store_resp
{
  adlb_data_code dc;
  struct packed_notif_counts notifs;
};

/**
 * Header for retrieve message
 */
struct packed_retrieve_hdr
{
  adlb_datum_id id;
  adlb_retrieve_refc refcounts;
  int subscript_len; // including null byte, 0 if no subscript
  char subscript[];
};

#define PACKED_SUBSCRIPT_MAX (ADLB_DATA_SUBSCRIPT_MAX + \
          sizeof(adlb_datum_id) + sizeof(int))
struct packed_insert_atomic_resp
{
  struct packed_notif_counts notifs;
  adlb_data_code dc;
  bool created;
  int value_len; // Value length, negative if not present
  adlb_data_type value_type;
};

struct pack_sub_resp
{
  adlb_data_code dc; // Error code
  bool subscribed; // True if not closed and subscribed
};

struct packed_size_req
{
  adlb_datum_id id;
  adlb_refc decr;
};

/*
  Generic boolean response for data op
 */
struct packed_bool_resp
{
  adlb_data_code dc;
  bool result;
};

/**
 * Response for container reference
 */
struct packed_cont_ref_resp
{
  adlb_data_code dc;
  struct packed_notif_counts notifs;
};

__attribute__((always_inline))
static inline int
xlb_pack_id_sub(void *buffer, adlb_datum_id id, adlb_subscript subscript);

__attribute__((always_inline))
static inline int
xlb_unpack_id_sub(const void *buffer, adlb_datum_id *id,
                  adlb_subscript *subscript);

/**
 * Request to probe for steal work
 */
struct packed_steal
{
  int max_memory;
  int64_t idle_check_attempt; // Sender's last idle check number
  // Sender's work type counts packed into sync_data field as int[]
};

#define WORK_TYPES_SIZE (sizeof(int) * (size_t)xlb_types_size)

struct packed_steal_resp
{
  int count; // number of work units
  bool last; // whether last set of stolen work
};

/*
  Notify server for closed data
 */
struct packed_notify_hdr
{
  adlb_datum_id id;
  int subscript_len;
  char subscript[]; // Small subscripts inline
};


/*
   Header for stolen task
 */
struct packed_steal_work
{
  int type;
  int priority;
  int putter;
  int answer;
  int target;
  int length;
  int parallelism;
};

/**
 Sync can contain various types of control messages
 */

/**
 Subscribe header for sync message.
 Include small subscripts inline
 */
#define PACKED_SUBSCRIBE_INLINE_BYTES 32

struct packed_subscribe_sync
{
  adlb_datum_id id;
  int subscript_len;
};

/**
 Sync can contain various types of control messages.
 These should be registered in sync.c for human-readable perf counters
 */
typedef enum
{
  ADLB_SYNC_REQUEST, // Sync for a regular request
  ADLB_SYNC_STEAL_PROBE, // Probe for work
  ADLB_SYNC_STEAL_PROBE_RESP, // Respond to probe
  ADLB_SYNC_STEAL, // Carry out steal
  ADLB_SYNC_REFCOUNT, // Modify reference count
  ADLB_SYNC_SUBSCRIBE, // Subscribe to a datum
  ADLB_SYNC_NOTIFY, // Notify after subscription to a datum
  ADLB_SYNC_SHUTDOWN, // Shutdown server

  ADLB_SYNC_ENUM_COUNT, // Dummy value: count of enum types
} adlb_sync_mode;

struct packed_sync
{
  adlb_sync_mode mode;
  union
  {
    struct packed_incr incr;   // if refcount increment
    struct packed_steal steal; // if steal
    struct packed_subscribe_sync subscribe; // if subscribe or notify
  };
  /* Extra data depending on sync type.  Same size used by all servers to
     allow for fixed-size buffers to be used */
  char sync_data[];
};

#define SYNC_DATA_SIZE \
  (WORK_TYPES_SIZE > PACKED_SUBSCRIBE_INLINE_BYTES ? \
   WORK_TYPES_SIZE : PACKED_SUBSCRIBE_INLINE_BYTES)
#define PACKED_SYNC_SIZE (sizeof(struct packed_sync) + SYNC_DATA_SIZE)

/**
   Simple data type transfer
 */
static inline void
xlb_pack_steal_work(struct packed_steal_work* p, xlb_work_unit* wu)
{
  p->answer = wu->answer;
  p->length = wu->length;
  p->priority = wu->priority;
  p->putter = wu->putter;
  p->target = wu->target;
  p->type = wu->type;
  p->parallelism = wu->parallelism;
}

/** Member count of enum adlb_tag */
#define XLB_MAX_TAGS 128

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
  ADLB_TAG_PUT_RULE,
  ADLB_TAG_GET,
  ADLB_TAG_IGET,

  // data operations
  ADLB_TAG_CREATE_HEADER,
  ADLB_TAG_MULTICREATE,
  ADLB_TAG_CREATE_PAYLOAD,
  ADLB_TAG_EXISTS,
  ADLB_TAG_STORE_HEADER,
  ADLB_TAG_STORE_SUBSCRIPT,
  ADLB_TAG_STORE_PAYLOAD,
  ADLB_TAG_RETRIEVE,
  ADLB_TAG_ENUMERATE,
  ADLB_TAG_SUBSCRIBE,
  ADLB_TAG_NOTIFY,
  ADLB_TAG_PERMANENT,
  ADLB_TAG_GET_REFCOUNTS,
  ADLB_TAG_REFCOUNT_INCR,
  ADLB_TAG_INSERT_ATOMIC,
  ADLB_TAG_UNIQUE,
  ADLB_TAG_TYPEOF,
  ADLB_TAG_CONTAINER_TYPEOF,
  ADLB_TAG_CONTAINER_REFERENCE,
  ADLB_TAG_CONTAINER_SIZE,
  ADLB_TAG_LOCK,
  ADLB_TAG_UNLOCK,
  ADLB_TAG_SYNC_REQUEST,
  ADLB_TAG_DO_NOTHING,
  ADLB_TAG_CHECK_IDLE,
  ADLB_TAG_BLOCK_WORKER,
  ADLB_TAG_SHUTDOWN_WORKER,

  /// tags outgoing from server
  ADLB_TAG_RESPONSE,
  ADLB_TAG_RESPONSE_PUT,
  ADLB_TAG_RESPONSE_GET,
  ADLB_TAG_RESPONSE_NOTIF,
  ADLB_TAG_RESPONSE_STEAL_COUNT,
  ADLB_TAG_RESPONSE_STEAL,
  ADLB_TAG_SYNC_RESPONSE,
  ADLB_TAG_SYNC_SUB,
  ADLB_TAG_WORKUNIT,
  ADLB_TAG_FAIL,

  /// tags that may be to/from server/worker
  /** Work unit payload */
  ADLB_TAG_WORK

} adlb_tag;

/*
 Inline functions for message packing
 */


/*
 Pack into buffer of size at least PACKED_SUBSCRIPT_MAX

 len: output variable for bytes stored in buffer
 returns the number of bytes used in buffer
 */
static inline int
xlb_pack_id_sub(void *buffer, adlb_datum_id id,
                adlb_subscript subscript)
{
  assert(buffer != NULL);
  char *pos = (char*)buffer;

  MSG_PACK_BIN(pos, id);

  bool has_subscript = subscript.key != NULL;
  int sub_packed_size = has_subscript ? (int)subscript.length : -1;
  
  MSG_PACK_BIN(pos, sub_packed_size);

  if (has_subscript)
  {
    memcpy(pos, subscript.key, (size_t)sub_packed_size); 
    pos += sub_packed_size;
  }

  long bytes_written = pos - (char*)buffer;
  assert(bytes_written <= INT_MAX);
  return (int)bytes_written;
}


/*
  Extract id and subscript from buffer
  NOTE: returned subscript is pointer into buffer
  return the number of bytes consumed from buffer
 */
static inline int
xlb_unpack_id_sub(const void *buffer, adlb_datum_id *id,
                  adlb_subscript *subscript)
{
  assert(buffer != NULL);

  const char *pos = (const char*)buffer;
  MSG_UNPACK_BIN(pos, id);

  int subscript_packed_len;
  MSG_UNPACK_BIN(pos, &subscript_packed_len);

  bool has_subscript = subscript_packed_len > 0;
  if (has_subscript)
  {
    subscript->key = pos;
    subscript->length = (size_t)subscript_packed_len;
    pos += subscript_packed_len;
  }
  else
  {
    subscript->key = NULL;
    subscript->length = 0;
  }
  long bytes_read = (pos - (const char*)buffer);
  assert(bytes_read <= INT_MAX);
  return (int)bytes_read;
}


// Revert to regular packing rules
#pragma pack(pop)
#endif
