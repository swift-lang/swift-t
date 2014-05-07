/*
 * FIPS 180-1 compliant SHA-1 implementation,
 * by Christophe Devine <devine@cr0.net>;
 * this program is licensed under the GPL.
 */

#include <stdio.h>
#include <string.h>
#include "devine_sha1.h"

void rng_init(RNG_state *newstate, int seed)
{
  struct sha1_context ctx;
  struct state_t gen;
  int i;

  for (i=0; i < 16; i++) 
    gen.state[i] = 0;
  gen.state[16] = 0xFF & (seed >> 24);
  gen.state[17] = 0xFF & (seed >> 16);
  gen.state[18] = 0xFF & (seed >> 8);
  gen.state[19] = 0xFF & (seed >> 0);
  
  sha1_starts(&ctx);
  sha1_update(&ctx, gen.state, 20);
  sha1_finish(&ctx, newstate);
}

void rng_spawn(RNG_state *mystate, RNG_state *newstate, int spawnnumber)
{
	struct sha1_context ctx;
	uint8  bytes[4];
	
	bytes[0] = 0xFF & (spawnnumber >> 24);
	bytes[1] = 0xFF & (spawnnumber >> 16);
	bytes[2] = 0xFF & (spawnnumber >> 8);
	bytes[3] = 0xFF & spawnnumber;
	
	sha1_starts(&ctx);
	sha1_update(&ctx, mystate, 20);
	sha1_update(&ctx, bytes, 4);
	sha1_finish(&ctx, newstate);
}

int rng_rand(RNG_state *mystate){
        int r;
	uint32 b =  (mystate[16] << 24) | (mystate[17] << 16)
		| (mystate[18] << 8) | (mystate[19] << 0);
	b = b & POS_MASK;
	
	r = (int) b;
	//printf("b: %d\t, r: %d\n", b, r);
	return r;
}

int rng_nextrand(RNG_state *mystate){
	struct sha1_context ctx;
	int r;
	uint32 b;

	sha1_starts(&ctx);
	sha1_update(&ctx, mystate, 20);
	sha1_finish(&ctx, mystate);
	b =  (mystate[16] << 24) | (mystate[17] << 16)
		| (mystate[18] << 8) | (mystate[19] << 0);
	b = b & POS_MASK;
	
	r = (int) b;
	return r;
}

/* condense state into string to display during debugging */
char * rng_showstate(RNG_state *state, char *s){
  sprintf(s,"%.2X%.2X...", state[0],state[1]);
  return s;
}

/* describe random number generator type into string */
int rng_showtype(char *strBuf, int ind) {
  ind += sprintf(strBuf+ind, "SHA-1 (state size = %ldB)",
                 sizeof(struct state_t));
  return ind;
}

/*
 *  Devine implementation from here forward
 *
 */

#define GET_UINT32(n,b,i)					\
{								\
    (n) = (uint32) ((uint8 *) b)[(i)+3]				\
      | (((uint32) ((uint8 *) b)[(i)+2]) <<  8)			\
      | (((uint32) ((uint8 *) b)[(i)+1]) << 16)			\
      | (((uint32) ((uint8 *) b)[(i)]  ) << 24);		\
}

#define PUT_UINT32(n,b,i)					\
{								\
    (((uint8 *) b)[(i)+3]) = (uint8) (((n)      ) & 0xFF);	\
    (((uint8 *) b)[(i)+2]) = (uint8) (((n) >>  8) & 0xFF);	\
    (((uint8 *) b)[(i)+1]) = (uint8) (((n) >> 16) & 0xFF);	\
    (((uint8 *) b)[(i)]  ) = (uint8) (((n) >> 24) & 0xFF);	\
}

void sha1_starts( struct sha1_context *ctx )
{
    ctx->total = 0;
    ctx->state[0] = 0x67452301;
    ctx->state[1] = 0xEFCDAB89;
    ctx->state[2] = 0x98BADCFE;
    ctx->state[3] = 0x10325476;
    ctx->state[4] = 0xC3D2E1F0;
}

void sha1_process( struct sha1_context *ctx, uint8 data[64] )
{
    uint32 temp, A, B, C, D, E, W[16];

    GET_UINT32( W[0],  data,  0 );
    GET_UINT32( W[1],  data,  4 );
    GET_UINT32( W[2],  data,  8 );
    GET_UINT32( W[3],  data, 12 );
    GET_UINT32( W[4],  data, 16 );
    GET_UINT32( W[5],  data, 20 );
    GET_UINT32( W[6],  data, 24 );
    GET_UINT32( W[7],  data, 28 );
    GET_UINT32( W[8],  data, 32 );
    GET_UINT32( W[9],  data, 36 );
    GET_UINT32( W[10], data, 40 );
    GET_UINT32( W[11], data, 44 );
    GET_UINT32( W[12], data, 48 );
    GET_UINT32( W[13], data, 52 );
    GET_UINT32( W[14], data, 56 );
    GET_UINT32( W[15], data, 60 );

#define S(x,n) ((x << n) | ((x & 0xFFFFFFFF) >> (32 - n)))

#define R(t)						\
(							\
    temp = W[(t -  3) & 0x0F] ^ W[(t - 8) & 0x0F] ^	\
	   W[(t - 14) & 0x0F] ^ W[ t      & 0x0F],	\
    ( W[t & 0x0F] = S(temp,1) )				\
)

#define P(a,b,c,d,e,x)					\
{							\
    e += S(a,5) + F(b,c,d) + K + x; b = S(b,30);	\
}

    A = ctx->state[0];
    B = ctx->state[1];
    C = ctx->state[2];
    D = ctx->state[3];
    E = ctx->state[4];

#define F(x,y,z) (z ^ (x & (y ^ z)))
#define K 0x5A827999

    P( A, B, C, D, E, W[0]  );
    P( E, A, B, C, D, W[1]  );
    P( D, E, A, B, C, W[2]  );
    P( C, D, E, A, B, W[3]  );
    P( B, C, D, E, A, W[4]  );
    P( A, B, C, D, E, W[5]  );
    P( E, A, B, C, D, W[6]  );
    P( D, E, A, B, C, W[7]  );
    P( C, D, E, A, B, W[8]  );
    P( B, C, D, E, A, W[9]  );
    P( A, B, C, D, E, W[10] );
    P( E, A, B, C, D, W[11] );
    P( D, E, A, B, C, W[12] );
    P( C, D, E, A, B, W[13] );
    P( B, C, D, E, A, W[14] );
    P( A, B, C, D, E, W[15] );
    P( E, A, B, C, D, R(16) );
    P( D, E, A, B, C, R(17) );
    P( C, D, E, A, B, R(18) );
    P( B, C, D, E, A, R(19) );

#undef K
#undef F

#define F(x,y,z) (x ^ y ^ z)
#define K 0x6ED9EBA1

    P( A, B, C, D, E, R(20) );
    P( E, A, B, C, D, R(21) );
    P( D, E, A, B, C, R(22) );
    P( C, D, E, A, B, R(23) );
    P( B, C, D, E, A, R(24) );
    P( A, B, C, D, E, R(25) );
    P( E, A, B, C, D, R(26) );
    P( D, E, A, B, C, R(27) );
    P( C, D, E, A, B, R(28) );
    P( B, C, D, E, A, R(29) );
    P( A, B, C, D, E, R(30) );
    P( E, A, B, C, D, R(31) );
    P( D, E, A, B, C, R(32) );
    P( C, D, E, A, B, R(33) );
    P( B, C, D, E, A, R(34) );
    P( A, B, C, D, E, R(35) );
    P( E, A, B, C, D, R(36) );
    P( D, E, A, B, C, R(37) );
    P( C, D, E, A, B, R(38) );
    P( B, C, D, E, A, R(39) );

#undef K
#undef F

#define F(x,y,z) ((x & y) | (z & (x | y)))
#define K 0x8F1BBCDC

    P( A, B, C, D, E, R(40) );
    P( E, A, B, C, D, R(41) );
    P( D, E, A, B, C, R(42) );
    P( C, D, E, A, B, R(43) );
    P( B, C, D, E, A, R(44) );
    P( A, B, C, D, E, R(45) );
    P( E, A, B, C, D, R(46) );
    P( D, E, A, B, C, R(47) );
    P( C, D, E, A, B, R(48) );
    P( B, C, D, E, A, R(49) );
    P( A, B, C, D, E, R(50) );
    P( E, A, B, C, D, R(51) );
    P( D, E, A, B, C, R(52) );
    P( C, D, E, A, B, R(53) );
    P( B, C, D, E, A, R(54) );
    P( A, B, C, D, E, R(55) );
    P( E, A, B, C, D, R(56) );
    P( D, E, A, B, C, R(57) );
    P( C, D, E, A, B, R(58) );
    P( B, C, D, E, A, R(59) );

#undef K
#undef F

#define F(x,y,z) (x ^ y ^ z)
#define K 0xCA62C1D6

    P( A, B, C, D, E, R(60) );
    P( E, A, B, C, D, R(61) );
    P( D, E, A, B, C, R(62) );
    P( C, D, E, A, B, R(63) );
    P( B, C, D, E, A, R(64) );
    P( A, B, C, D, E, R(65) );
    P( E, A, B, C, D, R(66) );
    P( D, E, A, B, C, R(67) );
    P( C, D, E, A, B, R(68) );
    P( B, C, D, E, A, R(69) );
    P( A, B, C, D, E, R(70) );
    P( E, A, B, C, D, R(71) );
    P( D, E, A, B, C, R(72) );
    P( C, D, E, A, B, R(73) );
    P( B, C, D, E, A, R(74) );
    P( A, B, C, D, E, R(75) );
    P( E, A, B, C, D, R(76) );
    P( D, E, A, B, C, R(77) );
    P( C, D, E, A, B, R(78) );
    P( B, C, D, E, A, R(79) );

#undef K
#undef F

    ctx->state[0] += A;
    ctx->state[1] += B;
    ctx->state[2] += C;
    ctx->state[3] += D;
    ctx->state[4] += E;
}

void sha1_update( struct sha1_context *ctx, uint8 *input, uint32 length )
{
    uint32 left, fill;

    if( ! length ) return;

    left = (uint32) (ctx->total & 0x3F);
    fill = 64 - left;

    ctx->total += length;

    if( left && length >= fill )
    {
	memcpy( (void *) (ctx->buffer + left), (void *) input, fill );
	sha1_process( ctx, ctx->buffer );
	length -= fill;
	input  += fill;
	left = 0;
    }

    while( length >= 64 )
    {
        sha1_process( ctx, input );
	length -= 64;
	input  += 64;
    }

    if( length )
    {
        memcpy( (void *) (ctx->buffer + left), (void *) input, length );
    }
}

static uint8 sha1_padding[64] =
{
 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
};

void sha1_finish( struct sha1_context *ctx, uint8 digest[16] )
{
    uint32 last, padn;
    uint8 msglen[8];

    PUT_UINT32( (uint32) ((ctx->total >> 29) & 0xFFFFFFFF), msglen, 0 );
    PUT_UINT32( (uint32) ((ctx->total <<  3) & 0xFFFFFFFF), msglen, 4 );

    last = (uint32) (ctx->total & 0x3F);
    padn = ( last < 56 ) ? ( 56 - last ) : ( 120 - last );

    sha1_update( ctx, sha1_padding, padn );
    sha1_update( ctx, msglen, 8 );

    PUT_UINT32( ctx->state[0], digest,  0 );
    PUT_UINT32( ctx->state[1], digest,  4 );
    PUT_UINT32( ctx->state[2], digest,  8 );
    PUT_UINT32( ctx->state[3], digest, 12 );
    PUT_UINT32( ctx->state[4], digest, 16 );
}


