
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <stdarg.h>
#include <stdbool.h>
#include <limits.h>

#include "xq.h"

#include "mpi.h"
#include "adlb-defs.h"

#include "version.h"

/** These versions indicators are not used by ExM */
#define  ADLB_VERSION             ADLBM
#define  ADLB_VERSION_NUMBER      420
#define  ADLB_VERSION_DATE        05-Jan-2012

#define ADLB_SUCCESS                     (1)
#define ADLB_ERROR                      (-1)
#define ADLB_NO_MORE_WORK       (-999999999)
#define ADLB_DONE_BY_EXHAUSTION (-999999998)
#define ADLB_NO_CURRENT_WORK    (-999999997)
#define ADLB_PUT_REJECTED       (-999999996)
#define ADLB_LOWEST_PRIO        (-999999999)
#define ADLB_RANK_ANY (-1)

/* for Info_get;  MUST match adlbf.h  */
#define ADLB_INFO_MALLOC_HWM               1
#define ADLB_INFO_AVG_TIME_ON_RQ           2
#define ADLB_INFO_NPUSHED_FROM_HERE        3
#define ADLB_INFO_NPUSHED_TO_HERE          4
#define ADLB_INFO_NREJECTED_PUTS           5
#define ADLB_INFO_LOOP_TOP_TIME            6
#define ADLB_INFO_MAX_QMSTAT_TRIP_TIME     7
#define ADLB_INFO_AVG_QMSTAT_TRIP_TIME     8
#define ADLB_INFO_NUM_QMS_EXCEED_INT       9
#define ADLB_INFO_NUM_RESERVES            10
#define ADLB_INFO_NUM_RESERVES_PUT_ON_RQ  11
#define ADLB_INFO_MAX_WQ_COUNT            12

#define ADLB_RESERVE_REQUEST_ANY    -1
#define ADLB_RESERVE_EOL            -1
#define ADLB_HANDLE_SIZE             5

/**
   Maximum size for a given ADLB transaction
*/
#define ADLB_MSG_MAX (1024*1024)
#define ADLB_DHT_MAX 1024*1024

int ADLBP_Init(int, int, int, int, int *, int *, int *, MPI_Comm *);
int ADLB_Init(int, int, int, int, int*, int *, int *, MPI_Comm *);

int ADLB_Version(version* output);

int ADLBP_Server(double hi_malloc, double periodic_logging_time);
int ADLB_Server(double hi_malloc, double periodic_logging_time);

int ADLBP_Debug_server(double timeout);
int ADLB_Debug_server(double timeout);

int ADLBP_Put(void *,int,int,int,int,int);
int ADLB_Put(void *,int,int,int,int,int);

// Applications should not call these directly but
// should use the typed forms defined below
int ADLBP_Create(long id, adlb_data_type type,
                 const char* filename, adlb_data_type subscript_type);
int ADLB_Create(long id, adlb_data_type type,
                const char* filename, adlb_data_type subscript_type);

int ADLB_Create_integer(adlb_datum_id id);

int ADLB_Create_float(adlb_datum_id id);

int ADLB_Create_string(adlb_datum_id id);

int ADLB_Create_blob(adlb_datum_id id);

int ADLB_Create_file(adlb_datum_id id, const char* filename);

int ADLB_Create_container(adlb_datum_id id, adlb_data_type subscript_type);

int ADLBP_Exists(adlb_datum_id id, bool* result);
int ADLB_Exists(adlb_datum_id id, bool* result);

int ADLBP_Store(adlb_datum_id id, void *data, int length);
int ADLB_Store(adlb_datum_id id, void *data, int length);

int ADLBP_Retrieve(adlb_datum_id id, adlb_data_type* type,
		   void *data, int *length);
int ADLB_Retrieve(adlb_datum_id id, adlb_data_type* type,
		  void *data, int *length);

int ADLBP_Enumerate(adlb_datum_id container_id,
                   int count, int offset,
                   char** subscripts, int* subscripts_length,
                   char** members, int* members_length,
                   int* records);
int ADLB_Enumerate(adlb_datum_id container_id,
                   int count, int offset,
                   char** subscripts, int* subscripts_length,
                   char** members, int* members_length,
                   int* records);

int ADLBP_Slot_create(adlb_datum_id id);
int ADLB_Slot_create(adlb_datum_id id);

int ADLBP_Slot_drop(adlb_datum_id id);
int ADLB_Slot_drop(adlb_datum_id id);

int ADLBP_Insert(adlb_datum_id id, const char *subscript,
                 const char* member, int member_length, int drops);
int ADLB_Insert(adlb_datum_id id, const char *subscript,
                const char* member, int member_length, int drops);

int ADLBP_Insert_atomic(adlb_datum_id id, const char *subscript,
                        bool* result);
int ADLB_Insert_atomic(adlb_datum_id id, const char *subscript,
                       bool* result);

int ADLBP_Lookup(adlb_datum_id id, const char *subscript, char* member, int* found);
int ADLB_Lookup(adlb_datum_id id, const char *subscript, char* member, int* found);

int ADLBP_Subscribe(adlb_datum_id id, int* subscribed);
int ADLB_Subscribe(adlb_datum_id id, int* subscribed);

int ADLBP_Container_reference(adlb_datum_id id, const char *subscript,
                              adlb_datum_id reference,
                              adlb_data_type ref_type);
int ADLB_Container_reference(adlb_datum_id id, const char *subscript,
                             adlb_datum_id reference,
                             adlb_data_type ref_type);

int ADLBP_Close(adlb_datum_id id, int** ranks, int* count);
/**
   Allocates fresh storage in ranks iff count > 0
 */
int ADLB_Close(adlb_datum_id id, int** ranks, int* count);

int ADLBP_Unique(adlb_datum_id *result);
int ADLB_Unique(adlb_datum_id *result);

int ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type);
int ADLB_Typeof(adlb_datum_id id, adlb_data_type* type);

int ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* type);
int ADLB_Container_typeof(adlb_datum_id id, adlb_data_type* type);

int ADLBP_Container_size(adlb_datum_id container_id, int* size);
int ADLB_Container_size(adlb_datum_id container_id, int* size);

int ADLBP_Lock(adlb_datum_id id, bool* result);
int ADLB_Lock(adlb_datum_id id, bool* result);

int ADLBP_Unlock(adlb_datum_id id);
int ADLB_Unlock(adlb_datum_id id);

void ADLB_Data_string_totype(const char* type_string,
                             adlb_data_type* type);

int ADLB_Data_type_tostring(char* output, adlb_data_type type);

int ADLBP_Reserve(int *, int *, int *, int *, int *, int *);
int ADLB_Reserve(int *, int *, int *, int *, int *, int *);

int ADLBP_Ireserve(int *, int *, int *, int *, int *, int *);
int ADLB_Ireserve(int *, int *, int *, int *, int *, int *);

int ADLBP_Get_reserved(void *, int *);
int ADLB_Get_reserved(void *, int *);

int ADLBP_Get_reserved_timed(void *, int *, double *);
int ADLB_Get_reserved_timed(void *, int *, double *);

int ADLBP_Begin_batch_put(void *, int);
int ADLBP_End_batch_put(void);

int ADLBP_Set_problem_done(void);
int ADLB_Set_problem_done(void);

int ADLBP_Set_no_more_work(void);  // deprecated
int ADLB_Set_no_more_work(void);

int ADLBP_Info_get(int, double *);
int ADLB_Info_get(int, double *);

int ADLBP_Info_num_work_units(int , int *, int *, int *);
int ADLB_Info_num_work_units(int , int *, int *, int *);

int ADLBP_Finalize(void);
int ADLB_Finalize(void);

int ADLBP_Abort(int);
int ADLB_Abort(int);

int adlbp_Probe(int , int, MPI_Comm, MPI_Status *);  /* used in aldb.c and adlb_prof.c */
int ADLB_Begin_batch_put(void *, int);  /* used in aldbf.c (note the f) and adlb_prof.c */
int ADLB_End_batch_put(void);           /* used in aldbf.c (note the f) and adlb_prof.c */

void adlbp_dbgprintf(int flag, int linenum, char *fmt, ...);
#ifndef NDEBUG
#define aprintf(flag,...) adlbp_dbgprintf(flag,__LINE__,__VA_ARGS__)
#else
#define aprintf(flag,...) // noop
#endif
void *dmalloc(int,const char *,int);
#define amalloc(nbytes)   dmalloc(nbytes,__FUNCTION__,__LINE__)
void dfree(void *,int,const char *,int);
#define afree(ptr,nbytes) dfree(ptr,nbytes,__FUNCTION__,__LINE__)

/**
   Most warnings will result in fatal errors at some point,
   but the user may turn these messages off
 */
#define ENABLE_WARN
#ifdef ENABLE_WARN
#define WARN(format, args...)              \
  {    printf("WARNING: ADLB: " format, ## args); \
       fflush(stdout);                    \
  }
#else
#define WARN(format, args...)
#endif

/*
   Debugging may be disabled at compile-time via NDEBUG or
   ENABLE_DEBUG or at run-time by setting environment variable ADLB_DEBUG=0
 */
extern int debug;
#ifndef NDEBUG
// #define ENABLE_DEBUG 1
#endif
#ifdef ENABLE_DEBUG
#define DEBUG(format, args...)              \
  { if (debug) {                            \
         printf("ADLB: " format "\n", ## args); \
         fflush(stdout);                    \
       } }
#else
#define DEBUG(format, args...) // noop
#endif

#ifndef NDEBUG
// #define ENABLE_TRACE 1
#endif
#ifdef ENABLE_TRACE
#define TRACE(format, args...)             \
  { if (debug) {                           \
  printf("ADLB_TRACE: " format "\n", ## args);  \
  fflush(stdout);                          \
  } }
#else
#define TRACE(format, args...) // noop
#endif
