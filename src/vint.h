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
 * vint.h
 *
 *  Created on: Aug 8, 2013
 *      Author: armstrong
 *
 *  Tools for encoding and decoding variable-length integer quantities.
 *
 *  Encoding format:
 *    We encode least significant bytes first (i.e. little-endian)
 *    first byte: 
 *      lower 6 bits: bits of integer
 *      7th bit: sign byte (1 for negative)
 *      8th bit: 1 if more bytes follow
 *    subsequent byte:
 *      lower 7 bites: bits of integer
 *      8th bit: 1 if more bytes follow
 */

#ifndef CUTILS_VINT_H
#define CUTILS_VINT_H

#include <stdbool.h>

#include "c-utils-types.h"

// Maximum size of cutil_long encoded in bytes
// One bit overhead per byte
#define VINT_MAX_BYTES (sizeof(cutil_long) + (sizeof(cutil_long) - 1) / 8 + 1)

#define VINT_MORE_MASK (0x80)
#define VINT_SIGN_MASK (0x40)
#define VINT_6BIT_MASK (0x3f)
#define VINT_7BIT_MASK (0x7f)

/*
  Return encoded length of a vint
 */
static inline int
vint_bytes(cutil_long val)
{
  int len = 1;
  if (val < 0)
    val = -val;
  val >>= 6; // Account for sign bit. 
  while (val != 0) {
    val >>= 7;
    len++;
  }
  return len;
}

/*
  Encode a vint.  Must have at least VINT_MAX_BYTES or vint_bytes(val)
  space in buffer, whichever is less.
  Returns number of bytes written
 */
static inline int
encode_vint(cutil_long val, unsigned char *buffer)
{
  unsigned char b; // Current byte being encoded
  bool more; // If more bytes are needed
  bool negative = val < 0;
  if (negative)
    val = -val;

  // First byte has 6 bits of number owing to sign bit
  b = val & VINT_6BIT_MASK;
  val >>= 6;

  if (negative)
    b |= VINT_SIGN_MASK;

  more = val != 0;
  if (more)
    b |= VINT_MORE_MASK;

  buffer[0] = b;
  int pos = 1;
  while (more)
  {
    b = val & VINT_7BIT_MASK;
    val >>= 7;
    more = val != 0;
    if (more)
      b |= VINT_MORE_MASK;
    buffer[pos++] = b;
  }
  return pos;
}

/*
  Decode a vint.  Returns number of bytes read, or negative on an error
 */
static inline int
decode_vint(unsigned char *buffer, int len, cutil_long *val)
{
  if (len < 1)
    return -1;
  unsigned char b = buffer[0]; // current byte
  cutil_long sign; // 1 for +ive, -1 for -ive

  sign = ((b & VINT_SIGN_MASK) != 0) ? -1 : 1;
  cutil_long accum = b & VINT_6BIT_MASK;

  int pos = 1; // Byte position
  int shift = 6; // Bits to shift next byte by
  
  while ((b & VINT_MORE_MASK) != 0)
  {
    if (len <= pos)
    {
      // Too long
      return -1;
    }
    b = buffer[pos++];
    cutil_long add = (cutil_long)(b & VINT_7BIT_MASK);
    // TODO: check for overflow
    accum += (add << shift);
    shift += 7;
  }
  *val = accum * sign;
  return pos;
}

#endif
