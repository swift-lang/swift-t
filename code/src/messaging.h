/**
 * messaging.h
 *
 *  Created on: Jun 7, 2012
 *      Author: wozniak
 *
 *  ADLB messaging conventions
 * */

#ifndef MESSAGING_H
#define MESSAGING_H

#include "adlb.h"

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

/**
   ADLB message tags
   Some RPCs require two incoming messages: a header and a payload
*/
typedef enum
{
  /// incoming tags

  // task operations
  ADLB_TAG_PUT_HEADER = 0,
  ADLB_TAG_PUT_PAYLOAD,
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
  ADLB_TAG_SHUTDOWN,

  /// outgoing tags
  ADLB_TAG_RESPONSE,
  ADLB_TAG_WORKUNIT,
  ADLB_TAG_ABORT

} adlb_tag;

#endif
