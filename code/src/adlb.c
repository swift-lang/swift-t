
#define _GNU_SOURCE
#include <assert.h>
#include <inttypes.h>
#include <stddef.h>
#include "adlb.h"
#include "mpi.h"
#include <signal.h>
#include <stdio.h>
#include <string.h>

#include "c-utils.h"
#include "data.h"

#define DBG_CHECK_TIME  30

#if defined(__bgp__) || defined(__bg__)
/* turn on checking unexpected queue, etc. */
#define DEBUGGING_BGP  1
#endif

#ifdef DEBUGGING_BGP
extern void **MPID_Recvq_unexpected_head_ptr;
int GetUnexpectedRequestCount( void );
void GetUnexpectedRequestTagsInDBGTagsBuf(int[]);
#endif

/* for sicortex */
#if defined(linux) && defined(mips)
#include <malloc.h>
#define DEBUGGING_SICORTEX 1
#endif

/* naming scheme:
    FA - from app
    TA - to app
    SS - server to server
*/
#define  FA_PUT_HDR                       1001
#define  FA_PUT_MSG                       1002
#define  FA_PUT_COMMON_HDR                1003
#define  FA_PUT_COMMON_MSG                1004
#define  FA_PUT_BATCH_DONE                1005
#define  FA_DID_PUT_AT_REMOTE             1006
#define  FA_RESERVE                       1007
#define  TA_RESERVE_RESP                  1008
#define  FA_GET_RESERVED                  1009
#define  TA_GET_RESERVED_RESP             1010
#define  FA_NO_MORE_WORK                  1011

/* data operations */
#define  FA_CREATE_HDR                    2001
#define  FA_CREATE_MSG                    2002
#define  FA_EXISTS_HDR                    2003
#define  FA_STORE_HDR                     2010
#define  FA_STORE_MSG                     2020
#define  FA_RETRIEVE_HDR                  2030
#define  FA_ENUMERATE_HDR                 2035
#define  FA_CLOSE_HDR                     2040
#define  FA_SUBSCRIBE_HDR                 2050
#define  FA_SLOT_CREATE_HDR               2060
#define  FA_SLOT_DROP_HDR                 2070
#define  FA_INSERT_HDR                    2080
#define  FA_INSERT_MSG                    2090
#define  FA_INSERT_ATOMIC_HDR             2095
#define  FA_LOOKUP_HDR                    2100
#define  FA_UNIQUE_HDR                    2110
#define  FA_TYPEOF_HDR                    2112
#define  FA_CONTAINER_TYPEOF_HDR          2120
#define  FA_CONTAINER_REFERENCE_HDR       2130
#define  FA_CONTAINER_SIZE_HDR            2140
#define  FA_LOCK_HDR                      2150
#define  FA_UNLOCK_HDR                    2160

#define  FA_LOCAL_APP_DONE                1012
/* #define  TA_RESEND_PUT                 1013 */
#define  SS_NO_MORE_WORK                  1014
#define  SS_QMSTAT                        1015
#define  SS_END_LOOP_1                    1016
#define  SS_END_LOOP_2                    1017
#define  SS_RFR                           1018
#define  SS_RFR_RESP                      1019
#define  TA_ACK_AND_RC                    1020
#define  SS_PUSH_QUERY                    1021
#define  SS_PUSH_QUERY_RESP               1022
#define  SS_PUSH_HDR                      1023
#define  SS_PUSH_WORK                     1024
#define  SS_PUSH_DEL                      1025
#define  SS_ADLB_ABORT                    1026
#define  FA_ADLB_ABORT                    1027
#define  SS_UNRESERVE                     1028
#define  SS_MOVING_TARGETED_WORK          1029
#define  FA_LOG                           1030
#define  DS_LOG                           1031
#define  DS_END                           1032
#define  SS_PERIODIC_STATS                1033
#define  SS_EXHAUST_CHK_LOOP_1            1034
#define  SS_EXHAUST_CHK_LOOP_2            1035
#define  SS_DONE_BY_EXHAUSTION            1036
#define  FA_INFO_NUM_WORK_UNITS           1037
#define  FA_GET_COMMON                    1038
#define  TA_GET_COMMON_RESP               1039
#define  SS_DBG_TIMING_MSG                1040

#define  SUCCESS                             1
#define  ERROR                              -1
#define  NO_CURR_WORK                       -2

#define  IBUF_NUMINTS                       12
#define  IBUF_NUMDBLS                       12
#define  RFRBUF_NUMINTS                   (12+REQ_TYPE_VECT_SZ)

#define  THRESHOLD_TO_START_PUSH          (0.66 * max_malloc)
#define  THRESHOLD_TO_STOP_PUT            (0.88 * max_malloc)

#define  MAX_PUT_ATTEMPTS                  100
#define  MAX_PUSH_ATTEMPTS                1000

#define  NUM_BUFS_IN_CIRCLE                 90

static char *svn_revision = "$Revision: 417 $";
static char *svn_date = "$LastChangedDate: 2011-02-07 08:26:54 -0600 (Mon, 07 Feb 2011) $";
// static char *svn_dummy = "A";  /* just to cause updates to above */

static int num_world_nodes, num_servers, next_wqseqno, next_rqseqno, next_cqseqno,
           no_more_work_flag, num_apps_this_server, num_app_ranks, num_types,
           master_server_rank, my_world_rank, holding_end_loop_1, my_server_rank,
           using_debug_server, debug_server_rank, *user_types, dbgprintf_flag;
static int nputmsgs = 0, nqmstatmsgs = 0, num_qmstats_exceeded_interval = 0;
static int npushed_from_here = 0, npushed_to_here = 0, nrfrs_sent = 0, nrfrs_recvd = 0;
static int num_rq_nodes_timed = 0, num_tq_nodes_fixed = 0;
static int *rfr_to_rank;  /* vector, one per app rank */
static int *rfr_out;
static int *dbg_rfr_sent_cnt;  /* vector, one per app rank */
static int dbg_max_msg_queue_cnt = 0;
static int num_events_since_logatds, num_ss_msgs_handled_since_logatds;
static int num_reserves_since_logatds, num_reserves_immed_sat_since_logatds;
static int num_reserves_not_in_stat_vec, num_rfr_failed_since_logatds;
static int common_len = 0, common_refcnt = 0,
           common_server_rank = -1, common_server_commseqno = -1;
static double init_fixed_dmalloced = 0.0, total_bytes_dmalloced = 0.0;
static double curr_bytes_dmalloced = 0.0, hwm_bytes_dmalloced = 0.0;
static double num_reserves = 0.0, num_reserves_put_on_rq = 0.0;
static double sum_of_qmstat_trip_times = 0.0, max_qmstat_trip_time = 0.0;
static double num_rejected_puts = 0.0, total_time_on_rq = 0.0;
static double max_malloc, job_start_time;
static MPI_Comm adlb_all_comm, adlb_server_comm, adlb_debug_comm;
static MPI_Request dummy_req;

// static int random_in_range(int,int);
static int get_type_idx(int);
static int find_cand_rank_with_worktype(int,int);
static void update_local_state();
static void pack_qmstat(void);
static void unpack_qmstat(void);
static void check_remote_work_for_queued_apps();
static int get_server_idx(int);
static int get_server_rank(int);
// static int dump_qmstat_info();
static void print_final_stats(void);
static void print_proc_self_status(void);
static void print_curr_mem_and_queue_status(void);
static void print_circular_buffers(void);
static void log_at_debug_server(void);
void adlb_exit_handler(void);
// static void cblog(int flag, int for_rank, char *fmt, ...);
static void adlb_server_abort(int,int);
int adlbp_Reserve(int *, int *, int *, int *, int *, int *, int);
int adlbp_Get_reserved_timed(void *, int *, double *);

static int put_internal(int work_type, int work_prio,
                        int answer_rank, int target_rank,
                        int work_len, void* work_buf,
                        int ws_hsr, int batch_flag, int ws_common_len,
                        int ws_common_server_rank,
                        int ws_common_server_commseqno,
                        int* exhausted_flag);

static int set_reference_and_notify(long id, long value);
static int close_notification(long id, int* ranks, int count);
static int slot_notification(long id);

struct qmstat_entry
{
    double nbytes_used;
    int qlen_unpin_untarg;
    int *type_hi_prio;
};
struct qmstat_entry *qmstat_tbl;
void *qmstat_send_buf, *qmstat_recv_buf;
int qmstat_buflen;

static int lhs_rank, rhs_rank;

static char *inside_batch_put;
static char *first_time_on_rq;
static double qmstat_interval = 0.1;
static double logatds_interval = 1.0;
static double total_looptop_time = 0.0;;
static int next_server_rank_for_put;
static double dbg_time_interval = 1.0;
static int dbg_unexpected_by_tag[50];
static int *dbg_wq_by_type;
static int *dbg_wq_targ_by_type;
static int *dbg_rfr_attempts_by_type;

/* for debugging with circular buffers */
static int use_circ_buffs = 0;  /* 1 turns on using circ buffs, etc */
static int use_dbg_prints = 1;  /* 1 turns on using DBG prints and code */
static int *bufidx, num_bufs_in_circle;
static char ***cbuffers, log_buf[111];

static int doing_periodic_stats = 0;  /* default is OFF */
static double periodic_timeout = 1.0;

int debug = 1;

#define XFER_SIZE (ADLB_MSG_MAX)
/** Reusable transfer buffer */
static char xfer[XFER_SIZE];

// MPE variables for server profiling
#if defined( LOG_ADLB_INTERNALS ) || defined( LOG_GUESS_USER_STATE )

#define LOG_ADLB_INTERNALS 1
#include <mpe.h>

#define MPE_SVR_LOGGING 1

extern int mpe_svr_put_start, mpe_svr_put_end;
extern int mpe_svr_create_start, mpe_svr_create_end;
extern int mpe_svr_store_start, mpe_svr_store_end;
extern int mpe_svr_retrieve_start, mpe_svr_retrieve_end;
extern int mpe_svr_subscribe_start, mpe_svr_subscribe_end;
extern int mpe_svr_close_start, mpe_svr_close_end;
extern int mpe_svr_unique_start, mpe_svr_unique_end;
extern int mpe_svr_reserve_start, mpe_svr_reserve_end;
extern int mpe_svr_get_start, mpe_svr_get_end;

#define MPE_LOG_EVENT(e) { printf("e: %i\n", e); MPE_Log_event(e,0,NULL); }

#else

#define MPE_LOG_EVENT(e) 0;

#endif

// The CHECK macros never report an error message in a correct
// program: thus, they may be disabled for performance

int x;

#ifdef ENABLE_DEBUG
/**
  Asserts that condition is true, else returns given error code.
  Note: This is disabled if ENABLE_DEBUG is not defined
*/
#define CHECK_MSG(rc, args...)                  \
  { if (!(rc)) {                                             \
      printf("CHECK FAILED: adlb.c line: %i\n", __LINE__);   \
      printf(args);                                          \
      return ADLB_ERROR; }}

/**
   Checks that an MPI return code is MPI_SUCCESS
   Note: This is disabled if ENABLE_DEBUG is not defined
 */
#define MPI_CHECK(rc)  \
  { if (rc != MPI_SUCCESS) { \
    printf("MPI_CHECK FAILED: adlb.c line: %i\n", __LINE__);\
    return ADLB_ERROR; }}

/**
   Checks that an ADLB return code is ADLB_SUCCESS
   Note: This is disabled if ENABLE_DEBUG is not defined
 */
#define ADLB_CHECK(rc) { if (rc != ADLB_SUCCESS) { \
    printf("ADLB_CHECK FAILED: adlb.c line: %i\n", __LINE__); \
    return rc; }}

/**
   Checks that an ADLB data return code is ADLB_DATA_SUCCESS
   Note: This is disabled if ENABLE_DEBUG is not defined
 */
#define ADLB_DATA_CHECK(dc) { if (dc != ADLB_DATA_SUCCESS) { \
    printf("ADLB_DATA_CHECK FAILED: adlb.c line: %i\n", __LINE__); \
    return ADLB_ERROR; }}
#else
// Make these noops for performance
#define CHECK_MSG(rc, args...)
#define MPI_CHECK(rc)
#define ADLB_CHECK(rc)
#define ADLB_DATA_CHECK(rc)
#endif

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

static void check_versions()
{
  version av, cuv, rcuv;
  // required c-utils version (rcuv):
  ADLB_Version(&av);
  version_parse(&rcuv, "0.0.1");
  c_utils_version(&cuv);
  version_require("ADLB", &av, "c-utils", &cuv, &rcuv);
}

int ADLBP_Init(int nservers, int use_debug_server, int aprintf_flag, int ntypes, int type_vect[],
               int *am_server, int *am_debug_server, MPI_Comm *app_comm)
{
    int i, j, rc;
    char *revp, *datep, srev[64], sdate[64];
    char temp_buf[512], print_buf[512000];

    check_versions();

    dbgprintf_flag = aprintf_flag;

    /* this ifdef is a temporary method for checking for sicortex (from David Gingold) */
#   if defined(linux) && defined(mips)
    signal(SIGSEGV,SIG_DFL);
    mallopt(M_TRIM_THRESHOLD, 8 * (1 << 20));  /* may require include malloc.h */
    mallopt(M_TOP_PAD, 8 * (1 << 20));
    mallopt(M_MMAP_THRESHOLD, 8 * (1 << 20));
#   endif

    setbuf(stdout,NULL);
    rc = MPI_Initialized(&i);
    if ( ! i)
    {
        printf("**** ADLB cannot function unless MPI is initialized first\n");
        return ADLB_ERROR;
    }
    /****
    rc = atexit(adlb_exit_handler);
    if (rc != 0)
    {
        printf("** FAILED TO SET UP EXIT HANDLER; rc %d\n",rc);
    }
    ****/

    // Allow user to disable debugging via environment variable
    char* t = getenv("ADLB_DEBUG");
    if (t != NULL && strcmp(t, "0") == 0)
    {
      // puts("debugging disabled...");
      debug = 0;
    }

    max_malloc = 500000000.0;  /* temp default; replaced in ADLB_Server */

    job_start_time = MPI_Wtime();

    rc = MPI_Comm_size(MPI_COMM_WORLD,&num_world_nodes);
    rc = MPI_Comm_rank(MPI_COMM_WORLD,&my_world_rank);
    if (my_world_rank == 0)
    {
        revp = memchr(svn_revision,' ',16);
        datep = memchr(svn_date,'(',64);
        strcpy(srev,revp);
        srev[strlen(srev)-1] = '\0';
        strcpy(sdate,datep);
        sdate[strlen(sdate)-1] = '\0';
        aprintf(1,"ADLB revision %4s %s\n",srev,sdate);
    }
    num_types = ntypes;
    user_types = amalloc(num_types * sizeof(int));
    for (i=0; i < num_types; i++)
        user_types[i] = type_vect[i];
    num_servers = nservers;
    using_debug_server = use_debug_server;
    if (using_debug_server)
    {
        debug_server_rank = num_world_nodes - 1;
        num_app_ranks = num_world_nodes - num_servers - 1;
        master_server_rank = num_world_nodes - num_servers - 1;
    }
    else
    {
        debug_server_rank = -1;
        num_app_ranks = num_world_nodes - num_servers;
        master_server_rank = num_world_nodes - num_servers;
    }
    if (my_world_rank < num_app_ranks)
    {
        *am_server = 0;
        *am_debug_server = 0;
        MPI_Comm_split(MPI_COMM_WORLD,0,my_world_rank,app_comm);
        my_server_rank = num_app_ranks + (my_world_rank % num_servers);
        aprintf(0000, "WORLD_RANK_OF_MY_SERVER %06d\n",my_server_rank);
    }
    else if (using_debug_server  &&  my_world_rank == (num_world_nodes-1))
    {
        *am_server = 0;
        *am_debug_server = 1;
        my_server_rank = -1;  /* don't have a server */
        MPI_Comm_split(MPI_COMM_WORLD,2,0,&adlb_debug_comm);
    }
    else
    {
        *am_server = 1;
        *am_debug_server = 0;
        my_server_rank = -1;  /* don't have a server; I am one */
        if (my_world_rank == (master_server_rank + num_servers - 1))  /* last server */
            rhs_rank = master_server_rank;
        else
            rhs_rank = my_world_rank + 1;
        if (my_world_rank == master_server_rank)
            lhs_rank = (master_server_rank + num_servers - 1);
        else
            lhs_rank = my_world_rank - 1;
        MPI_Comm_split(MPI_COMM_WORLD,1,my_world_rank-num_app_ranks,&adlb_server_comm);
        wq = (xq_t *) xq_create();    /* wq is defined in adlb-specific of xq.h */
        rq = (xq_t *) xq_create();    /* rq is defined in adlb-specific of xq.h */
        iq = (xq_t *) xq_create();    /* iq is defined in adlb-specific of xq.h */
        tq = (xq_t *) xq_create();    /* tq is defined in adlb-specific of xq.h */
        cq = (xq_t *) xq_create();    /* cq is defined in adlb-specific of xq.h */
        num_apps_this_server = 0;
        strcpy(print_buf,"SERVER for ranks: ");
        for (i=0; i < num_app_ranks; i++)
        {
            if ((num_app_ranks + (i % num_servers)) == my_world_rank)
            {
                num_apps_this_server++;
                sprintf(temp_buf,"%d ",i);
                strcat(print_buf,temp_buf);
            }
        }
        aprintf(1,"%s\n",print_buf);
        qmstat_tbl = amalloc(sizeof(struct qmstat_entry) * num_servers);
        for (i=0; i < num_servers; i++)
        {
            qmstat_tbl[i].type_hi_prio = amalloc(sizeof(int) * num_types);
            for (j=0; j < num_types; j++)
                qmstat_tbl[i].type_hi_prio[j] = ADLB_LOWEST_PRIO;
            qmstat_tbl[i].qlen_unpin_untarg = 0;
            qmstat_tbl[i].nbytes_used = 0.0;
        }
        qmstat_buflen = sizeof(int)*num_types +    /* qmstat type_hi_prio */
                        sizeof(int)           +    /* qmstat qlen_unpin_untarg */
                        sizeof(double);            /* qmstat nbytes_used */
        qmstat_buflen *= num_servers;
        // aprintf(0000,"qmstat buflen %d\n",qmstat_buflen);
        // dump_qmstat_info();    // COMMENT OUT
        qmstat_recv_buf = amalloc(qmstat_buflen);
    }
    rc = MPI_Comm_dup(MPI_COMM_WORLD,&adlb_all_comm);
    assert(rc == MPI_SUCCESS);
    next_wqseqno = 1;
    next_rqseqno = 1;
    next_cqseqno = 1;
    no_more_work_flag = 0;
    if (my_world_rank == master_server_rank)
        holding_end_loop_1 = 1;
    else
        holding_end_loop_1 = 0;
    inside_batch_put = amalloc(num_app_ranks * sizeof(char));
    first_time_on_rq = amalloc(num_app_ranks * sizeof(char));
    for (i=0; i < num_app_ranks; i++)
    {
        inside_batch_put[i] = 0;
        first_time_on_rq[i] = 1;
    }
    srandom(my_world_rank+1);  /* 1 is the default */
    rfr_to_rank = amalloc(num_app_ranks * sizeof(int));
    rfr_out = amalloc(num_world_nodes * sizeof(int));
    for (i=0; i < num_app_ranks; i++)
        rfr_to_rank[i] = -1;
    for (i=0; i < num_app_ranks; i++)
        rfr_out[i] = 0;
    if (use_dbg_prints)
    {
        dbg_wq_by_type = amalloc(10010 * sizeof(int));           /* enough for steve's types */
        dbg_wq_targ_by_type = amalloc(10010 * sizeof(int));      /* enough for steve's types */
        dbg_rfr_attempts_by_type = amalloc(10010 * sizeof(int)); /* enough for steve's types */
        dbg_rfr_sent_cnt = amalloc(num_app_ranks * sizeof(int));
        for (i=0; i < num_app_ranks; i++)
            dbg_rfr_sent_cnt[i] = 0;
        for (i=0; i < 50; i++)
            dbg_unexpected_by_tag[i] = 0;
        for (i=0; i < 10010; i++)
        {
            dbg_wq_by_type[i] = 0;
            dbg_wq_targ_by_type[i] = 0;
            dbg_rfr_attempts_by_type[i] = 0;
        }
    }
    init_fixed_dmalloced = curr_bytes_dmalloced;

    num_bufs_in_circle = NUM_BUFS_IN_CIRCLE;
    if (use_circ_buffs)
    {
        bufidx = malloc(num_world_nodes*sizeof(int));
        /* may want to NOT get buffers for all ranks; just for those locally */
        cbuffers = malloc(num_world_nodes*sizeof(char**));
        for (i=0; i < num_world_nodes; i++)
        {
            bufidx[i] = 0;
            cbuffers[i] = malloc(num_bufs_in_circle*sizeof(char*));
            for (j=0; j < num_bufs_in_circle; j++)
            {
                cbuffers[i][j] = (char *)malloc(100);
                cbuffers[i][j][0] = '\0';
            }
        }
    }
    next_server_rank_for_put = my_server_rank;

    return ADLB_SUCCESS;
}

int ADLB_Version(version* output)
{
  version_parse(output, "0.0.2");
  return ADLB_SUCCESS;
}

int ADLBP_Server(double hi_malloc, double periodic_log_interval)
{
    int i, j, k, rc, rc1, rc2, done, reserve_buf[REQ_TYPE_VECT_SZ+1], info_buf[IBUF_NUMINTS], work_type,
        answer_rank, work_prio, wqseqno, work_len, flag, from_rank, from_tag, hang_flag,
        num_local_apps_done, type_idx, server_rank, orig_rqseqno,
        server_idx, target_rank, cand_rank, msg_available, rqseqno, push_attempt_cntr,
        ack_buf[IBUF_NUMINTS], batch_flag, for_rank,
        req_types[REQ_TYPE_VECT_SZ], push_query_is_out, to_rank, prev_target,
        *temp_buf, rfr_buf[RFRBUF_NUMINTS], nbytes_printed, nbytes_left_to_print, skip,
        periodic_buf_num_ints, *periodic_buf, *periodic_rq_vector, **periodic_wq_2darray,
        *periodic_put_cnt, *periodic_resolved_reserve_cnt, exhausted_flag,
        qmstat_msg_is_out, iprobe_successful_cnt;
    adlb_data_code dc;
    /****************
    int ptidx, max_ptidx, prio_tags[8];
    ****************/
    int dbg_msg_is_out, dbg_tags_handled[50];
    double dbls_info_buf[IBUF_NUMDBLS], *dbls_temp_buf, smallest_dbl, temp_dbl;
    xq_node_t *wq_node, *rq_node, *iq_node, *iq_curr_node, *iq_next_node,
              *tq_node, *tq_prev, *cq_node;
    // xq_node_t *qmstat_isend_node_ptr;
    wq_struct_t *ws;
    rq_struct_t *rs;
    iq_struct_t *is;
    tq_struct_t *ts;
    cq_struct_t *cs;
    void *work_buf;
    MPI_Status status;
    MPI_Request request, qmstat_req;
    MPI_Request *temp_req;
    char *temp_char_buf, *buf1000, temp_str[64];
    double prev_periodic_msg_time, prev_qmstat_msg_time;
    double prev_dbg_msg_timelen, prev_dbg_msg_start, dbg_prev_qmstat_timelen;
    double prev_exhaust_chk_time, exhaust_chk_interval, start_looptop_time;
    double prev_logatds_time;
    struct qmstat_entry temp_qm;

    int dbg_flag;
    int dbg_2_cnt, dbg_10_cnt, dbg_10002_cnt;
    double dbg_30_time, dbg_oldest_2_time, dbg_oldest_10_time, dbg_oldest_10002_time;
    char dbg_temp_buf[512], dbg_print_buf[4096];

    data_init(num_servers, my_world_rank);

#ifdef FOO_BGP
    tmaxmem = malloc(sizeof(void*) * 9000);
    for (i=0; i < 9000; i++)
    {
        tmaxmem[i] = malloc(1000000);
        if ( ! tmaxmem[i] )
            break;
    }
    max_malloc = (double) (i-1) * 1000000;
    j = i;
    for (i=0; i < j; i++)
        free(tmaxmem[i]);
    free(tmaxmem);
    // max_malloc = 4096;    /* for debug testing */
#endif
    max_malloc = hi_malloc;
    if (periodic_log_interval > 0.0)
    {
        doing_periodic_stats = 1;
        periodic_timeout = periodic_log_interval;
    }
    aprintf(1,"lhs %d  rhs %d  max_malloc %0.f  periodic_log_interval %f\n",
            lhs_rank,rhs_rank,max_malloc,periodic_log_interval);

    if (doing_periodic_stats)
    {
        if (my_world_rank == master_server_rank)
        {
            aprintf(1,"INFO_APS: ntypes=%d nappranks=%d\n",num_types,num_app_ranks);
            for (i=0; i < num_types; i++)
                aprintf(1,"INFO_APS: type_idx=%d type_val=%d\n",i,user_types[i]);
        }
        periodic_buf_num_ints = (num_types+2) +                   /* wq_2darray stuff */
                                ((num_app_ranks+1)*num_types) +   /* rq_vector stuff */
                                num_types +                       /* put_cnt stuff */
                                num_types;                        /* resolved_reserve stuff */
        periodic_buf = amalloc( periodic_buf_num_ints * sizeof(int) );
        periodic_wq_2darray = amalloc(num_types * sizeof(int*) );
        for (i=0; i < num_types; i++)
        {
            periodic_wq_2darray[i] = amalloc((num_app_ranks+1) * sizeof(int) );
            for (j=0; j < (num_app_ranks+1); j++)
                periodic_wq_2darray[i][j] = 0;
        }
        periodic_rq_vector = amalloc((num_types+2) * sizeof(int) );
        for (i=0; i < (num_types+2); i++)
            periodic_rq_vector[i] = 0;
        periodic_put_cnt = amalloc(num_types * sizeof(int) );
        for (i=0; i < num_types; i++)
            periodic_put_cnt[i] = 0;
        periodic_resolved_reserve_cnt = amalloc(num_types * sizeof(int) );
        for (i=0; i < num_types; i++)
            periodic_resolved_reserve_cnt[i] = 0;
        buf1000 = amalloc(1001);   /* one extra to hold end-of-string char */
    }
    num_events_since_logatds = 0;
    num_reserves_since_logatds = 0;
    num_reserves_immed_sat_since_logatds = 0;
    num_reserves_not_in_stat_vec = 0;
    num_rfr_failed_since_logatds = 0;
    num_ss_msgs_handled_since_logatds = 0;
    prev_logatds_time = MPI_Wtime();
    prev_periodic_msg_time = MPI_Wtime();
    prev_qmstat_msg_time = MPI_Wtime();
    prev_dbg_msg_start = MPI_Wtime();
    prev_exhaust_chk_time = MPI_Wtime();
    start_looptop_time = 0.0;  /* setup below */
    // Default: May be overridden below:
    exhaust_chk_interval = 5.0;
    exhausted_flag = 0;
    push_attempt_cntr = 0;
    push_query_is_out = 0;
    num_local_apps_done = 0;
    temp_qm.type_hi_prio = amalloc(num_types * sizeof(int));
    qmstat_msg_is_out = 0;
    iprobe_successful_cnt = 0;
    if (use_dbg_prints)
    {
        dbg_msg_is_out = 0;
        for (i=0; i < 50; i++)
            dbg_tags_handled[i] = 0;
        dbg_30_time = MPI_Wtime();
    }

    // Allow user to override ADLB exhaustion interval
    char *s = getenv("ADLB_EXHAUST_TIME");
    if (s != NULL &&
        strlen(s) > 0)
    {
      int c = sscanf(s, "%lf", &exhaust_chk_interval);
      if (c != 1 || exhaust_chk_interval <= 0)
      {
        printf("Illegal value of ADLB_EXHAUST_TIME!\n");
        return ADLB_ERROR;
      }
    }

    qmstat_req = MPI_REQUEST_NULL;
    done = 0;
    while ( ! done )
    {
        if (curr_bytes_dmalloced > THRESHOLD_TO_START_PUSH)
        {
            if ( ! push_query_is_out  &&  num_servers > 1)
            {
                wq_node = wq_find_unpinned();
                if (wq_node)
                {
                    cand_rank = -1;
                    smallest_dbl = 999999999999.9;
                    for (i=0; i < num_servers; i++)
                    {
                        server_rank = get_server_rank(i);
                        if (server_rank != my_world_rank
                        &&  qmstat_tbl[i].nbytes_used < THRESHOLD_TO_START_PUSH
                        &&  qmstat_tbl[i].nbytes_used < smallest_dbl)
                        {
                            smallest_dbl = qmstat_tbl[i].nbytes_used;
                            cand_rank = server_rank;
                        }
                    }
                    if (cand_rank >= 0)
                    {
                        ws = wq_node->data;
                        dbls_temp_buf    = amalloc(IBUF_NUMINTS * sizeof(double));
                        dbls_temp_buf[0] = (double) ws->work_type;
                        dbls_temp_buf[1] = (double) ws->work_prio;
                        dbls_temp_buf[2] = (double) ws->work_len;
                        dbls_temp_buf[3] = (double) ws->answer_rank;
                        dbls_temp_buf[4] = (double) ws->time_stamp;
                        dbls_temp_buf[5] = (double) ws->target_rank;  // PTW: now need remotely
                        dbls_temp_buf[6] = (double) ws->home_server_rank;  // PTW:
                        dbls_temp_buf[7] = (double) ws->wqseqno;
                        dbls_temp_buf[8]  = (double) ws->common_len;
                        dbls_temp_buf[9]  = (double) ws->common_server_rank;
                        dbls_temp_buf[10] = (double) ws->common_server_commseqno;
                        temp_req = amalloc(sizeof(MPI_Request));
                        MPI_Isend(dbls_temp_buf,IBUF_NUMDBLS,MPI_DOUBLE,cand_rank,
                                  SS_PUSH_QUERY,adlb_all_comm,temp_req);
                        iq_node = iq_node_create(temp_req,(IBUF_NUMDBLS * sizeof(double)),
                                                 dbls_temp_buf);
                        iq_append(iq_node);
                        push_query_is_out = 1;
                        push_attempt_cntr++;
                    }
                }
            }
        }

        if (use_dbg_prints  &&  (MPI_Wtime() - dbg_30_time) > DBG_CHECK_TIME)
        {
            dbg_30_time = MPI_Wtime();
            for (rq_node=xq_first(rq); rq_node; rq_node=xq_next(rq,rq_node))
            {
                rs = rq_node->data;
                if ((dbg_30_time - rs->time_stamp) > DBG_CHECK_TIME)
                {
                    cand_rank = -1;
                    for (i=0; i < REQ_TYPE_VECT_SZ; i++)
                    {
                        if (rs->req_types[i] < -1)  /* invalid type as place-holder */
                            break;
                        cand_rank = find_cand_rank_with_worktype(rs->world_rank,rs->req_types[i]);
                        if (cand_rank >= 0)
                            break;
                    }
                    if (cand_rank >= 0)
                        dbg_flag = 1;
                    else
                        dbg_flag = 0;
                    sprintf(dbg_print_buf,"%d %f %d %d %d %d ",
                            rs->rqseqno,dbg_30_time-rs->time_stamp,rs->world_rank,
                            rfr_to_rank[rs->world_rank],dbg_rfr_sent_cnt[rs->world_rank],
                            dbg_flag);
                    for (i=0; i < REQ_TYPE_VECT_SZ; i++)
                    {
                        if (rs->req_types[i] >= 0)
                        {
                            sprintf(dbg_temp_buf,"%d ",rs->req_types[i]);
                            strcat(dbg_print_buf,dbg_temp_buf);
                        }
                        else
                            break;
                    }
                    aprintf(0000,"DBG1: %s\n",dbg_print_buf);
                }
            }
            dbg_2_cnt = dbg_10_cnt = dbg_10002_cnt = 0;
            dbg_oldest_2_time = dbg_oldest_10_time = dbg_oldest_10002_time = 999999999999.0;
            for (wq_node=xq_first(wq); wq_node; wq_node=xq_next(wq,wq_node))
            {
                ws = wq_node->data;
                if (ws->work_type == 2)
                {
                    dbg_2_cnt++;
                    if (ws->time_stamp < dbg_oldest_2_time)  // older
                        dbg_oldest_2_time = ws->time_stamp;
                }
                else if (ws->work_type == 10)
                {
                    dbg_10_cnt++;
                    if (ws->time_stamp < dbg_oldest_10_time)  // older
                        dbg_oldest_10_time = ws->time_stamp;
                }
                else if (ws->work_type == 2)
                {
                    dbg_10002_cnt++;
                    if (ws->time_stamp < dbg_oldest_10002_time)  // older
                        dbg_oldest_10002_time = ws->time_stamp;
                }
            }
            if (dbg_oldest_2_time < 999999999999.0)
                dbg_oldest_2_time = MPI_Wtime() - dbg_oldest_2_time;
            else
                dbg_oldest_2_time = 0.0;
            if (dbg_oldest_10_time < 999999999999.0)
                dbg_oldest_10_time = MPI_Wtime() - dbg_oldest_10_time;
            else
                dbg_oldest_10_time = 0.0;
            if (dbg_oldest_10002_time < 999999999999.0)
                dbg_oldest_10002_time = MPI_Wtime() - dbg_oldest_10002_time;
            else
                dbg_oldest_10002_time = 0.0;
            aprintf(0000,"DBG2: %d %f %d %f %d %f\n",
                    dbg_2_cnt,dbg_oldest_2_time,
                    dbg_10_cnt,dbg_oldest_10_time,
                    dbg_10002_cnt,dbg_oldest_10002_time);

            /**/
            for (wq_node=xq_first(wq); wq_node; wq_node=xq_next(wq,wq_node))
            {
                ws = wq_node->data;
                dbg_wq_by_type[ws->work_type]++;
                if (ws->target_rank >= 0)
                    dbg_wq_targ_by_type[ws->work_type]++;
            }
            sprintf(dbg_print_buf,"%d; ",wq->count);
            for (i=0; i < 10010; i++)
            {
                if (dbg_wq_by_type[i] > 0)
                {
                    sprintf(dbg_temp_buf,"%d:%d ",i,dbg_wq_by_type[i]);
                    strcat(dbg_print_buf,dbg_temp_buf);
                }
                if (dbg_wq_targ_by_type[i] > 0)
                {
                    sprintf(dbg_temp_buf,"%dt:%d ",i,dbg_wq_targ_by_type[i]);
                    strcat(dbg_print_buf,dbg_temp_buf);
                }
            }
            aprintf(0000,"DBG8: %s\n",dbg_print_buf);
            for (i=0; i < 10010; i++)
                dbg_wq_by_type[i] = dbg_wq_targ_by_type[i] = 0;
            /**/
            dbg_print_buf[0] = '\0';
            for (i=0; i < 10010; i++)
            {
                if (dbg_rfr_attempts_by_type[i] > 0)
                {
                    sprintf(dbg_temp_buf,"%d:%d ",i,dbg_rfr_attempts_by_type[i]);
                    strcat(dbg_print_buf,dbg_temp_buf);
                }
            }
            aprintf(0000,"DBG9: %s\n",dbg_print_buf);
            for (i=0; i < 10010; i++)
                dbg_rfr_attempts_by_type[i] = 0;
            /**/

            sprintf(dbg_print_buf,"%d; ",iprobe_successful_cnt);
            for (i=0; i < 50; i++)
            {
                if (dbg_tags_handled[i] > 0)
                {
                    sprintf(dbg_temp_buf,"%d:%d ",i+1000,dbg_tags_handled[i]);
                    strcat(dbg_print_buf,dbg_temp_buf);
                }
            }
            aprintf(0000,"DBG5: %s\n",dbg_print_buf);
            iprobe_successful_cnt = 0;
            for (i=0; i < 50; i++)
                dbg_tags_handled[i] = 0;

#           ifdef DEBUGGING_BGP
            GetUnexpectedRequestTagsInDBGTagsBuf(dbg_unexpected_by_tag);
            sprintf(dbg_print_buf,"%d; ",dbg_max_msg_queue_cnt);
            for (i=0; i < 50; i++)
            {
                if (dbg_unexpected_by_tag[i] > 0)
                {
                    sprintf(dbg_temp_buf,"%d:%d ",i+1000,dbg_unexpected_by_tag[i]);
                    strcat(dbg_print_buf,dbg_temp_buf);
                }
            }
            aprintf(0000,"DBG6: %s\n",dbg_print_buf);
            dbg_max_msg_queue_cnt = 0;
            for (i=0; i < 50; i++)
                dbg_unexpected_by_tag[i] = 0;
#           endif
#           ifdef DEBUGGING_SICORTEX
            aprintf(1,"DBG6: early queue len %d\n",MPIDI_Debug_early_queue_length());
#           endif
        }  /* use_dbg_prints etc. */

        if (doing_periodic_stats  &&
            my_world_rank == master_server_rank  &&
            (MPI_Wtime() - prev_periodic_msg_time) > periodic_timeout)
        {
            temp_buf = amalloc(periodic_buf_num_ints * sizeof(int));
            temp_req = amalloc(sizeof(MPI_Request));
            /* put in wq_2darray stuff */
            for (i=0; i < num_types; i++)
            {
                for (j=0; j < (num_app_ranks+1); j++)
                {
                    k = (i * (num_app_ranks+1)) + j;
                    temp_buf[k] = periodic_wq_2darray[i][j];
                }
            }
            /* put in rq_vector stuff */
            skip = (num_app_ranks + 1) * num_types;  /* skip wq_2d */
            for (i=0; i < (num_types+2); i++)
            {
                k = i + skip;
                temp_buf[k] = periodic_rq_vector[i];
            }
            /* put in put_cnt stuff */
            skip += num_types + 2;
            for (i=0; i < num_types; i++)
            {
                k = i + skip;
                temp_buf[k] = periodic_put_cnt[i];
            }
            /* put in resolved_reserve stuff */
            skip += num_types;
            for (i=0; i < num_types; i++)
            {
                k = i + skip;
                temp_buf[k] = periodic_resolved_reserve_cnt[i];
            }
            MPI_Isend(temp_buf,periodic_buf_num_ints,MPI_INT,rhs_rank,
                      SS_PERIODIC_STATS,adlb_all_comm,temp_req);
            iq_node = iq_node_create(temp_req,periodic_buf_num_ints*sizeof(int),temp_buf);
            iq_append(iq_node);
            prev_periodic_msg_time = MPI_Wtime();
        }
        if (my_world_rank == master_server_rank  &&
            (MPI_Wtime()-prev_exhaust_chk_time) > exhaust_chk_interval)
        {
            if (rq->count >= num_apps_this_server)
            {
                if (num_servers == 1)
                {
                    // send exhausted to apps on rq
                    while ((rq_node=xq_first(rq)))
                    {
                        rs = rq_node->data;
                        aprintf(0000,"SENDING NMW to rank %06d\n",rs->world_rank);
                        info_buf[0] = ADLB_DONE_BY_EXHAUSTION;
                        MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                                  TA_RESERVE_RESP,adlb_all_comm);
                        /* since exhaustion, do NOT alter times for total_time_on_rq */
                        rq_delete(rq_node);
                        /* done, so not dealing with periodic stats right now */
                    }
                }
                else
                {
                    exhausted_flag = 1;
                    temp_req = amalloc(sizeof(MPI_Request));
                    MPI_Isend(NULL,0,MPI_INT,rhs_rank,SS_EXHAUST_CHK_LOOP_1,
                              adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, 0, NULL);
                    iq_append(iq_node);
                }
            }
            prev_exhaust_chk_time = MPI_Wtime();
        }
        if (iq->count > 0)    /* if outstanding isends */
        {
            // if (qmstat_isend_node_ptr)  // check first; may check again below
            // {
                // is = qmstat_isend_node_ptr->data;
                // rc = MPI_Test(is->mpi_req,&flag,&status);
            // }
            for (iq_curr_node=xq_first(iq); iq_curr_node; iq_curr_node=iq_next_node)
            {
                iq_next_node = xq_next(iq,iq_curr_node);
                is = iq_curr_node->data;
                rc = MPI_Test(is->mpi_req,&flag,&status);
                if (flag)
                {
                    // if (iq_curr_node == qmstat_isend_node_ptr)
                        // qmstat_isend_node_ptr = NULL;
                    iq_delete(iq_curr_node);  /* also deletes non-NULL buf */
                }
            }
        }
        if  (num_servers > 1  &&
             my_world_rank == master_server_rank  &&
             (MPI_Wtime() - prev_qmstat_msg_time) > qmstat_interval)
        {
            if ( ! qmstat_msg_is_out )
            {
                qmstat_send_buf = amalloc(qmstat_buflen);
                pack_qmstat();  /* all info from qmstat_tbl into qmstat_send_buf */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Isend(qmstat_send_buf,qmstat_buflen,MPI_PACKED,rhs_rank,SS_QMSTAT,
                               adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,qmstat_buflen,qmstat_send_buf);
                iq_append(iq_node);
                qmstat_msg_is_out = 1;
                prev_qmstat_msg_time = MPI_Wtime();
            }
        }
        if  (use_dbg_prints                       &&
             num_servers > 1                      &&
             my_world_rank == master_server_rank  &&
             (MPI_Wtime() - prev_dbg_msg_start) > dbg_time_interval)
        {
            if ( ! dbg_msg_is_out )
            {
                dbls_temp_buf = amalloc(IBUF_NUMDBLS * sizeof(double));
                dbls_temp_buf[0] = MPI_Wtime();  /* loop start time */
                dbls_temp_buf[1] = MPI_Wtime();  /* hop  start time */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Isend(dbls_temp_buf,IBUF_NUMDBLS,MPI_DOUBLE,rhs_rank,SS_DBG_TIMING_MSG,
                               adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req, IBUF_NUMDBLS*sizeof(double), dbls_temp_buf);
                iq_append(iq_node);
                dbg_msg_is_out = 1;
                prev_dbg_msg_start = MPI_Wtime();
            }
        }
        if  (using_debug_server  &&
             num_events_since_logatds > 0  &&
             (MPI_Wtime() - prev_logatds_time) > logatds_interval)
        {
            log_at_debug_server();
            num_events_since_logatds = 0;
            num_reserves_since_logatds = 0;
            num_reserves_immed_sat_since_logatds = 0;
            num_reserves_not_in_stat_vec = 0;
            num_rfr_failed_since_logatds = 0;
            num_ss_msgs_handled_since_logatds = 0;
            prev_logatds_time = MPI_Wtime();
        }

        if (start_looptop_time == 0.0)
            start_looptop_time = MPI_Wtime();
        if (qmstat_req == MPI_REQUEST_NULL)
        {
            MPI_Irecv(qmstat_recv_buf,qmstat_buflen,MPI_PACKED,MPI_ANY_SOURCE,
                      SS_QMSTAT,adlb_all_comm,&qmstat_req);
        }
        /* do PMPI_xxxx calls to avoid frequent logging */
        rc = PMPI_Test(&qmstat_req,&msg_available,&status);
        if ( ! msg_available )
            rc = PMPI_Iprobe(MPI_ANY_SOURCE,MPI_ANY_TAG,adlb_all_comm,&msg_available,&status);
        if ( ! msg_available )
            continue;

#ifdef DEBUGGING_BGP
        if (GetUnexpectedRequestCount() > dbg_max_msg_queue_cnt)
            dbg_max_msg_queue_cnt = GetUnexpectedRequestCount();
#else
#       ifdef DEBUGGING_SICORTEX
        if (MPIDI_Debug_early_queue_length() > dbg_max_msg_queue_cnt)
            dbg_max_msg_queue_cnt = MPIDI_Debug_early_queue_length();
#       else
        dbg_max_msg_queue_cnt = 0;
#endif
#endif
        if (use_dbg_prints)
            dbg_tags_handled[status.MPI_TAG-1000]++;

        iprobe_successful_cnt++;
        total_looptop_time += MPI_Wtime() - start_looptop_time;
        start_looptop_time = 0.0;

        from_rank = status.MPI_SOURCE;
        from_tag = status.MPI_TAG;
        if (from_tag == FA_PUT_HDR)
        {

          MPE_LOG_EVENT(mpe_svr_put_start);
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,FA_PUT_HDR,adlb_all_comm,&status);
            if (using_debug_server)
                num_events_since_logatds++;
            if (no_more_work_flag)
            {
                ack_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Rsend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
                // aprintf(0000, "IN FA_PUT SENT NO_MORE_WORK TO %d\n",from_rank);
                continue;
            }
            if (curr_bytes_dmalloced > THRESHOLD_TO_STOP_PUT)
            {

                // aprintf(0000, "IN FA_PUT REJECTING PUT FROM RANK %06d\n",from_rank);
                // cblog(1,from_rank,"REJECTED PUT type %d\n",info_buf[0]);
                num_rejected_puts += 1;
                ack_buf[0] = ADLB_PUT_REJECTED;
                cand_rank = -1;
                smallest_dbl = 999999999999.9;
                for (i=0; i < num_servers; i++)
                {
                    server_rank = get_server_rank(i);
                    if (server_rank != my_world_rank
                    &&  qmstat_tbl[i].nbytes_used < THRESHOLD_TO_START_PUSH
                    &&  qmstat_tbl[i].nbytes_used < smallest_dbl)
                    {
                        smallest_dbl = qmstat_tbl[i].nbytes_used;
                        cand_rank = server_rank;
                    }
                }
                if (cand_rank >= 0)
                    ack_buf[1] = cand_rank;
                else
                    ack_buf[1] = -1;
                MPI_Rsend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
                continue;
            }
            work_type    = info_buf[0];
            work_prio    = info_buf[1];
            answer_rank  = info_buf[2];
            target_rank  = info_buf[3];
            work_len     = info_buf[4];
            work_buf     = amalloc(work_len);
            MPI_Irecv(work_buf,work_len,MPI_BYTE,from_rank,FA_PUT_MSG,adlb_all_comm,&request);
            ack_buf[0] = SUCCESS;
            MPI_Rsend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
            rc = MPI_Wait(&request,&status);

            //    ws->home_server_rank        = info_buf[5];  /* esp for targeted work */
            //     batch_flag                  = info_buf[6];  /* in batch but not nec with common */
            //     ws->common_len              = info_buf[7];
            //     ws->common_server_rank      = info_buf[8];
            //     ws->common_server_commseqno = info_buf[9];

            int ws_hsr        = info_buf[5];  /* esp for targeted work */
            int batch_flag                  = info_buf[6];  /* in batch but not nec with common */
            int ws_common_len              = info_buf[7];
            int ws_common_server_rank      = info_buf[8];
            int ws_common_server_commseqno = info_buf[9];

            put_internal(work_type,
                          work_prio,
                          answer_rank,
                          target_rank,
                          work_len,
                          work_buf,
                          ws_hsr,
                          batch_flag,
                          ws_common_len,
                          ws_common_server_rank,
                          ws_common_server_commseqno,
                          &exhausted_flag);
            ack_buf[0] = SUCCESS;
            MPI_Send(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
            // cblog(1,from_rank,"PAST PUT type %d targrank %d\n",work_type,target_rank);
            prev_exhaust_chk_time = MPI_Wtime();  /* not exhausted yet */
            MPE_LOG_EVENT(mpe_svr_put_end);
        }
        else if (from_tag == FA_CREATE_HDR)
        {
          MPE_LOG_EVENT(mpe_svr_create_start);
          TRACE("FA_CREATE_HDR\n");
          struct packed_id_type data;

          rc1 = MPI_Recv(&data, sizeof(struct packed_id_type),
                         MPI_BYTE, from_rank, FA_CREATE_HDR,
                         adlb_all_comm, &status);
          MPI_CHECK(rc1);

          long id = data.id;
          adlb_data_type type = data.type;

          dc = data_create(id, type);

          rc1 = MPI_Rsend(&dc, 1, MPI_INT, from_rank,
                          TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc1);

          // Types file and container need additional information
          if (type == ADLB_DATA_TYPE_FILE)
          {
            MPI_Recv(xfer, XFER_SIZE, MPI_CHAR, from_rank,
                     FA_CREATE_MSG, adlb_all_comm, &status);
            data_create_filename(id, xfer);
          }
          else if (type == ADLB_DATA_TYPE_CONTAINER)
          {
            adlb_data_type container_type;
            MPI_Recv(&container_type, 1, MPI_INT, from_rank,
                     FA_CREATE_MSG, adlb_all_comm, &status);
            data_create_container(id, container_type);
          }
          prev_exhaust_chk_time = MPI_Wtime();
          // DEBUG("CREATE: <%li> %s\n", id, (char*) work_buf);
          TRACE("FA_CREATE_HDR done\n");
          MPE_LOG_EVENT(mpe_svr_create_end);
        }

        else if (from_tag == FA_EXISTS_HDR)
        {
          TRACE("FA_EXISTS_HDR\n");
          bool result;
          adlb_datum_id id;

          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank, FA_EXISTS_HDR,
                        adlb_all_comm, &status);
          MPI_CHECK(rc);

          data_exists(id, &result);

          rc = MPI_Rsend(&result, sizeof(bool), MPI_BYTE, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
        }
        else if (from_tag == FA_STORE_HDR)
        {
          MPE_LOG_EVENT(mpe_svr_store_start);
          long id;
          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank, FA_STORE_HDR,
                         adlb_all_comm, &status);
          MPI_CHECK(rc);

          DEBUG("Store: <%li>", id);

          rc = MPI_Recv(xfer, XFER_SIZE, MPI_BYTE, from_rank,
                        FA_STORE_MSG, adlb_all_comm, &status);
          MPI_CHECK(rc);

          int length;
          MPI_Get_count(&status, MPI_BYTE, &length);

//          printf("l: %i\n", length);
//
//          double* d = (double*) &xfer[0];
//          DEBUG("dp: %p\n", d);
//          // DEBUG("d: %f\n", *d);
//          DEBUG("dx: %f\n", *(double*) xfer);

          dc = data_store(id, xfer, length);

          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank, TA_ACK_AND_RC,
                         adlb_all_comm);
          MPI_CHECK(rc);
	  TRACE("STORE DONE");
	  MPE_LOG_EVENT(mpe_svr_store_end);
        }
        else if (from_tag == FA_SLOT_CREATE_HDR)
        {
          long id;
          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank,
                         FA_SLOT_CREATE_HDR, adlb_all_comm, &status);
          MPI_CHECK(rc);

          dc = data_slot_create(id);
          DEBUG("Slot_create: <%li>", id);

          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank, TA_ACK_AND_RC,
                         adlb_all_comm);
        }
        else if (from_tag == FA_SLOT_DROP_HDR)
        {
          long id;
          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank,
                         FA_SLOT_DROP_HDR, adlb_all_comm, &status);
          MPI_CHECK(rc);

          int slots;
          DEBUG("Slot_drop: <%li>", id);
          dc = data_slot_drop(id, &slots);

          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);

          if (slots == 0)
            slot_notification(id);
        }
        else if (from_tag == FA_INSERT_HDR)
        {
          rc = MPI_Recv(xfer, ADLB_DATA_SUBSCRIPT_MAX+128, MPI_CHAR,
                        from_rank, FA_INSERT_HDR, adlb_all_comm,
                        &status);
          MPI_CHECK(rc);
          char subscript[ADLB_DATA_SUBSCRIPT_MAX];
          long id;
          char* member;
          int  member_length;
          int  drops;
          int n;
          n = sscanf(xfer, "%li %s %i %i",
                     &id, subscript, &member_length, &drops);
          // This can only fail on an internal error:
          assert(n == 4);

          member = malloc((member_length+1) * sizeof(char));

          long* references;
          int count, slots;

          rc = MPI_Recv(member, ADLB_DATA_MEMBER_MAX, MPI_CHAR,
                        from_rank, FA_INSERT_MSG, adlb_all_comm,
                        &status);
          MPI_CHECK(rc);

          DEBUG("Insert: <%li>[\"%s\"]=\"%s\"",
                id, subscript, member);

          dc = data_insert(id, subscript, member, drops,
                            &references, &count, &slots);
          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank, TA_ACK_AND_RC,
                         adlb_all_comm);
          MPI_CHECK(rc);

          if (dc == ADLB_DATA_SUCCESS)
          {
            if (count > 0)
            {
              long m;
              n = sscanf(member, "%li", &m);
              assert(n == 1);
              for (i = 0; i < count; i++)
                set_reference_and_notify(references[i], m);
              free(references);
            }
            if (slots == 0)
              slot_notification(id);
          }
          TRACE("INSERT DONE")
        }
        else if (from_tag == FA_INSERT_ATOMIC_HDR)
        {
          rc = MPI_Recv(xfer, ADLB_DATA_SUBSCRIPT_MAX+128, MPI_CHAR,
                        from_rank, FA_INSERT_ATOMIC_HDR,
                        adlb_all_comm, &status);
          MPI_CHECK(rc);
          char subscript[ADLB_DATA_SUBSCRIPT_MAX];
          long id;
          int n = sscanf(xfer, "%li %s", &id, subscript);
          // This can only fail on an internal error:
          assert(n == 2);

          bool result;
          dc = data_insert_atomic(id, subscript, &result);
          DEBUG("Insert_atomic: <%li>[\"%s\"] => %i",
                id, subscript, result);
          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          rc = MPI_Rsend(&result, sizeof(bool), MPI_BYTE, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
        }
        else if (from_tag == FA_RETRIEVE_HDR)
        {
          MPE_LOG_EVENT(mpe_svr_retrieve_start);
          TRACE("FA_RETRIEVE\n");
          long id;
          MPI_Recv(&id, 1, MPI_LONG, from_rank, FA_RETRIEVE_HDR,
                   adlb_all_comm, &status);

          void* result = NULL;
          int length;
	  adlb_data_type type;
          dc = data_retrieve(id, &type, &result, &length);
          MPI_Rsend(&dc, 1, MPI_INT, from_rank,
                    TA_ACK_AND_RC, adlb_all_comm);
          if (dc == ADLB_DATA_SUCCESS)
          {
	    MPI_Send(&type, 1, MPI_INT, from_rank,
		     TA_ACK_AND_RC, adlb_all_comm);
            MPI_Send(result, length, MPI_BYTE, from_rank,
                     TA_ACK_AND_RC, adlb_all_comm);
	    if (type == ADLB_DATA_TYPE_CONTAINER)
	      free(result);
            DEBUG("Retrieve: <%li>", id);
          }
          TRACE("FA_RETRIEVE done\n");
          MPE_LOG_EVENT(mpe_svr_retrieve_end);
        }
        else if (from_tag == FA_ENUMERATE_HDR)
        {
          TRACE("ENUMERATE_HDR\n");
          struct packed_enumerate opts;
          rc = MPI_Recv(&opts, sizeof(struct packed_enumerate),
                        MPI_BYTE, from_rank, FA_ENUMERATE_HDR,
                        adlb_all_comm, &status);
          MPI_CHECK(rc);

          char* subscripts =
            (void*) (opts.request_subscripts ? NULL+1 : NULL);
          char* members =
            (void*) (opts.request_members ? NULL+1 : NULL);
          int subscripts_length;
          int members_length;
          int actual;
          dc = data_enumerate(opts.id, opts.count, opts.offset,
                              &subscripts, &subscripts_length,
                              &members, &members_length, &actual);
          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          if (dc == ADLB_DATA_SUCCESS)
          {
            rc = MPI_Send(&actual, 1, MPI_INT, from_rank,
                          TA_ACK_AND_RC, adlb_all_comm);
            MPI_CHECK(rc);
            if (opts.request_subscripts)
            {
              rc = MPI_Send(subscripts, subscripts_length+1,
                            MPI_BYTE, from_rank,
                            TA_ACK_AND_RC, adlb_all_comm);
              MPI_CHECK(rc);
              free(subscripts);
            }
            if (opts.request_members)
            {
              rc = MPI_Send(members, members_length,
                            MPI_BYTE, from_rank,
                            TA_ACK_AND_RC, adlb_all_comm);
              MPI_CHECK(rc);
              free(members);
            }
          }
        }
        else if (from_tag == FA_LOOKUP_HDR)
        {
          TRACE("FA_LOOKUP\n");
          char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
          char subscript[ADLB_DATA_SUBSCRIPT_MAX];
          char* member;
          rc = MPI_Recv(msg, ADLB_DATA_SUBSCRIPT_MAX+32, MPI_BYTE,
                        from_rank, FA_LOOKUP_HDR,adlb_all_comm,&status);
          MPI_CHECK(rc);

          long id;
          int n = sscanf(msg, "%li %s", &id, subscript);
          assert(n == 2);

          dc = data_lookup(id, subscript, &member);

          struct packed_code_length p;
          p.code = dc;
          // Set this field to 1 if we found the entry, else -1
          bool found = (member != ADLB_DATA_ID_NULL);
          p.length = found ? 1 : -1;

          rc = MPI_Rsend(&p, sizeof(p), MPI_BYTE, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);

          if (dc == ADLB_DATA_SUCCESS && found)
          {
            rc = MPI_Send(member, strlen(member)+1, MPI_CHAR, from_rank,
                          TA_ACK_AND_RC, adlb_all_comm);
            MPI_CHECK(rc);
          }
          prev_exhaust_chk_time = MPI_Wtime();
          // DEBUG("LOOKUP: <%li>[\"%s\"] => <%li>\n",
          //       id, subscript, member);
          TRACE("FA_LOOKUP done\n");
        }
        else if (from_tag == FA_SUBSCRIBE_HDR)
        {
          MPE_LOG_EVENT(mpe_svr_subscribe_start);
          TRACE("FA_SUBSCRIBE\n");

          long id;
          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank,
                        FA_SUBSCRIBE_HDR, adlb_all_comm, &status);
          MPI_CHECK(rc);

          int result;
          dc = data_subscribe(id, from_rank, &result);
          if (dc != ADLB_DATA_SUCCESS)
            result = -1;
          rc = MPI_Rsend(&result, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);

          prev_exhaust_chk_time = MPI_Wtime();
          TRACE("FA_SUBSCRIBE done\n");
          MPE_LOG_EVENT(mpe_svr_subscribe_end);
        }
        else if (from_tag == FA_CONTAINER_REFERENCE_HDR)
        {
          rc = MPI_Recv(xfer, XFER_SIZE, MPI_BYTE, from_rank,
                        FA_CONTAINER_REFERENCE_HDR, adlb_all_comm,
                        &status);
          MPI_CHECK(rc);

          long container_id;
          char subscript[ADLB_DATA_SUBSCRIPT_MAX];
          long reference;
          int n = sscanf(xfer, "%li %li %s",
                         &reference, &container_id, subscript);
          assert(n == 3);

          DEBUG("Container_reference: <%li>[\"%s\"] => <%li>",
                container_id, subscript, reference);

          long member;
          dc = data_container_reference(container_id, subscript,
                                        reference, &member);
          if (dc == ADLB_DATA_SUCCESS)
            if (member != 0)
              set_reference_and_notify(reference, member);

          rc = MPI_Rsend(&dc, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          prev_exhaust_chk_time = MPI_Wtime();
        }
        else if (from_tag == FA_CONTAINER_SIZE_HDR)
        {
          long container_id;
          rc = MPI_Recv(&container_id, 1, MPI_LONG, from_rank,
                        FA_CONTAINER_SIZE_HDR, adlb_all_comm,
                        &status);
          MPI_CHECK(rc);

          int size;
          dc = data_container_size(container_id, &size);
          DEBUG("CONTAINER_SIZE: <%li> => <%i>",
                container_id, size);

          if (dc != ADLB_DATA_SUCCESS)
            size = -1;
          rc = MPI_Rsend(&size, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          prev_exhaust_chk_time = MPI_Wtime();
        }
        else if (from_tag == FA_CLOSE_HDR)
        {
          MPE_LOG_EVENT(mpe_svr_close_start);
          long id;
          MPI_Recv(&id, 1, MPI_LONG, from_rank, FA_CLOSE_HDR,
                   adlb_all_comm, &status);

          DEBUG("Close: <%li>", id);

          int* ranks;
          int count;
          dc = data_close(id, &ranks, &count);
          if (dc != ADLB_DATA_SUCCESS)
            count = -1;
          rc = MPI_Rsend(&count, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);

          if (count > 0)
          {
            rc = MPI_Send(ranks, count, MPI_INT, from_rank,
                          TA_ACK_AND_RC, adlb_all_comm);
            MPI_CHECK(rc);
            free(ranks);
          }
          prev_exhaust_chk_time = MPI_Wtime();
          MPE_LOG_EVENT(mpe_svr_close_end);
        }
        else if (from_tag == FA_LOCK_HDR)
        {
          long id;
          MPI_Recv(&id, 1, MPI_LONG, from_rank, FA_LOCK_HDR,
                   adlb_all_comm, &status);

          DEBUG("Lock: <%li> by rank: %i", id, from_rank);

          bool result;
          dc = data_lock(id, from_rank, &result);
          char c;
          if (dc == ADLB_DATA_SUCCESS)
          {
            if (result)
              c = '1';
            else
              c = '0';
          }
          else
            c = 'x';
          rc = MPI_Rsend(&c, 1, MPI_CHAR, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
        }
        else if (from_tag == FA_UNLOCK_HDR)
        {
          long id;
          MPI_Recv(&id, 1, MPI_LONG, from_rank, FA_UNLOCK_HDR,
                   adlb_all_comm, &status);

          DEBUG("Unlock: <%li> by rank: %i ", id, from_rank);

          dc = data_unlock(id);

          char c = (dc == ADLB_DATA_SUCCESS) ? '1' : 'x';
          rc = MPI_Rsend(&c, 1, MPI_CHAR, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
        }
        else if (from_tag == FA_UNIQUE_HDR)
        {
          MPE_LOG_EVENT(mpe_svr_unique_start);
          int msg;
          rc = MPI_Recv(&msg, 1, MPI_INT, from_rank, FA_UNIQUE_HDR,
                        adlb_all_comm, &status);
          MPI_CHECK(rc);

          long id;
          dc = data_unique(&id);

          rc = MPI_Rsend(&id, 1, MPI_LONG, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          prev_exhaust_chk_time = MPI_Wtime();
          // DEBUG("UNIQUE: <%li>\n", id);
          MPE_LOG_EVENT(mpe_svr_unique_end);
        }
        else if (from_tag == FA_TYPEOF_HDR)
        {
          adlb_datum_id id;
          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank,
                        FA_TYPEOF_HDR, adlb_all_comm,
                        &status);
          MPI_CHECK(rc);

          adlb_data_type type;
          dc = data_typeof(id, &type);
          if (dc != ADLB_DATA_SUCCESS)
            type = -1;

          rc = MPI_Rsend(&type, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          prev_exhaust_chk_time = MPI_Wtime();
        }
        else if (from_tag == FA_CONTAINER_TYPEOF_HDR)
        {
          adlb_datum_id id;
          rc = MPI_Recv(&id, 1, MPI_LONG, from_rank,
                        FA_CONTAINER_TYPEOF_HDR, adlb_all_comm,
                        &status);
          MPI_CHECK(rc);

          adlb_data_type type;
          dc = data_container_typeof(id, &type);
          if (dc != ADLB_DATA_SUCCESS)
            type = -1;

          rc = MPI_Rsend(&type, 1, MPI_INT, from_rank,
                         TA_ACK_AND_RC, adlb_all_comm);
          MPI_CHECK(rc);
          prev_exhaust_chk_time = MPI_Wtime();
        }
        else if (from_tag == FA_PUT_COMMON_HDR)
        {
          printf("PUT_COMMON!\n");
          // aprintf(0000, "AT FA_PUT_COMMON from %d\n",from_rank);
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (using_debug_server)
                num_events_since_logatds++;
            if (no_more_work_flag)
            {
                ack_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Ssend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
                // aprintf(0000, "SENT NO_MORE_WORK TO %06d\n",from_rank);
                continue;
            }
            if (curr_bytes_dmalloced > THRESHOLD_TO_STOP_PUT)
            {
                // aprintf(0000, "IN FA_PUT_COMMON REJECTING FROM RANK %06d\n",from_rank);
                // cblog(1,from_rank,"REJECTED PUT_COMMON type %d\n",info_buf[0]);
                num_rejected_puts += 1;
                ack_buf[0] = ADLB_PUT_REJECTED;
                cand_rank = -1;
                smallest_dbl = 999999999999.9;
                for (i=0; i < num_servers; i++)
                {
                    server_rank = get_server_rank(i);
                    if (server_rank != my_world_rank
                    &&  qmstat_tbl[i].nbytes_used < THRESHOLD_TO_START_PUSH
                    &&  qmstat_tbl[i].nbytes_used < smallest_dbl)
                    {
                        smallest_dbl = qmstat_tbl[i].nbytes_used;
                        cand_rank = server_rank;
                    }
                }
                if (cand_rank >= 0)
                    ack_buf[1] = cand_rank;
                else
                    ack_buf[1] = -1;
                MPI_Rsend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
                continue;
            }
            ack_buf[0] = SUCCESS;
            MPI_Ssend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
            inside_batch_put[from_rank] = 1;
            common_len = info_buf[0];
            work_buf = amalloc(common_len);
            MPI_Recv(work_buf,common_len,MPI_BYTE,from_rank,
                     FA_PUT_COMMON_MSG,adlb_all_comm,&status);
            cq_node = cq_node_create(common_len,work_buf,next_cqseqno);
            cq_append(cq_node);
            next_cqseqno++;
            ack_buf[0] = SUCCESS;
            ack_buf[1] = next_cqseqno - 1;
            MPI_Ssend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
            // aprintf(0000, "PAST FA_PUT_COMMON\n");
        }
        else if (from_tag == FA_PUT_BATCH_DONE)
        {
          // aprintf(0000, "AT FA_PUT_BATCH_DONE\n");
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            inside_batch_put[from_rank] = 0;
            if (info_buf[0] > 0)  /* common_cqseqno */
            {
                cq_node = cq_find_seqno(info_buf[0]);
                cs = cq_node->data;
                cs->refcnt = info_buf[1];
                if (cs->refcnt == cs->ngets)
                    cq_delete(cq_node);
            }
            if (using_debug_server)
                num_events_since_logatds++;
            if (no_more_work_flag)
            {
                ack_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Ssend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
                aprintf(0000, "SENT NO_MORE_WORK TO %06d\n",from_rank);
                continue;
            }
            ack_buf[0] = SUCCESS;
            MPI_Ssend(ack_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,adlb_all_comm);
            // aprintf(0000, "PAST FA_PUT_BATCH_DONE\n");
        }
        else if (from_tag == FA_DID_PUT_AT_REMOTE)
        {
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            work_type   = info_buf[0];
            target_rank = info_buf[1];
            server_rank = info_buf[2];
            tq_node = tq_find_rtr(target_rank,work_type,server_rank);
            if (tq_node)
            {
                ts = tq_node->data;
                ts->num_stored++;
            }
            else
            {
                tq_node = tq_node_create(target_rank,work_type,server_rank,1);
                tq_append(tq_node);
                ts = tq_node->data;
            }
            check_remote_work_for_queued_apps();  /* make sure no one is waiting for this */
        }
        else if (from_tag == FA_RESERVE)
        {
          MPE_LOG_EVENT(mpe_svr_reserve_start);
            aprintf(0000, "AT FA_RESERVE\n");
            MPI_Recv(reserve_buf,REQ_TYPE_VECT_SZ+1,MPI_INT,from_rank,FA_RESERVE,
                     adlb_all_comm,&status);
            num_reserves++;
            if (using_debug_server)
            {
                num_events_since_logatds++;
                num_reserves_since_logatds++;
            }
            if (no_more_work_flag)
            {
                info_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Send(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_RESERVE_RESP,adlb_all_comm);
                aprintf(0000, "IN FA_RESERVE SENTa NO_MORE_WORK TO %06d\n",from_rank);
                continue;
            }
            hang_flag = reserve_buf[0];
            for (i=0; i < REQ_TYPE_VECT_SZ; i++)
                req_types[i] = reserve_buf[i+1];
            // cblog(1,from_rank,"AT RESERVE types %d %d %d %d\n",
                       // req_types[0],req_types[1],req_types[2],req_types[3]);
            wq_node = wq_find_pre_targeted_hi_prio(from_rank,req_types);
            if ( ! wq_node)
                wq_node = wq_find_hi_prio(req_types);
            if (wq_node)
            {
                ws = wq_node->data;
                ws->pin_rank = from_rank;
                if (ws->pin_rank >= 0)
                    ws->pinned = 1;
                info_buf[0] = SUCCESS;
                info_buf[1] = ws->work_type;
                info_buf[2] = ws->work_prio;
                info_buf[3] = ws->work_len;
                info_buf[4] = ws->answer_rank;
                info_buf[5] = ws->wqseqno;
                info_buf[6] = my_world_rank;
                info_buf[7] = ws->common_len;
                info_buf[8] = ws->common_server_rank;
                info_buf[9] = ws->common_server_commseqno;
                aprintf(0000, "IN FA_RESERVE SENDING RESERVATION to %06d\n",from_rank);
                MPI_Send(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_RESERVE_RESP,adlb_all_comm);
                if (use_dbg_prints)
                    aprintf(0000,"DBG3: rsv -1 0.0 %f %d %d\n",
                            MPI_Wtime()-ws->time_stamp,from_rank,ws->work_type);
                if (using_debug_server)
                    num_reserves_immed_sat_since_logatds++;
                /* do not delete here because merely targeted; not given away */
                if (doing_periodic_stats)
                {
                    type_idx = get_type_idx(ws->work_type);
                    if (type_idx < 0) aprintf(1,"** invalid type\n");
                    periodic_resolved_reserve_cnt[type_idx]++;
                }
            }
            else
            {
                if (hang_flag)
                {
                    aprintf(0000,"QUEUEING rank %06d\n",from_rank);
                    // cblog(1,from_rank,"  QUEUED by %d\n",my_world_rank);
                    rqseqno = next_rqseqno++;
                    rq_node = rq_node_create(from_rank,req_types,rqseqno);
                    rs = rq_node->data;
                    rs->time_stamp = MPI_Wtime();
                    /** this small block is solely for computing counters for debug_server **/
                    cand_rank = -1;  /* default: did not find server that may have this type */
                    for (i=0; i < REQ_TYPE_VECT_SZ; i++)
                    {
                        if (req_types[i] < -1)  /* invalid type as place-holder */
                            break;
                        cand_rank = find_cand_rank_with_worktype(rs->world_rank,req_types[i]);
                        if (cand_rank >= 0)
                            break;
                    }
                    if (cand_rank < 0)
                        num_reserves_not_in_stat_vec++;
                    /** end debug block **/
                    if (doing_periodic_stats)
                    {
                        for (i=0; i < num_types; i++)
                        {
                            if (i == 0  && rs->req_types[i] < 0)  /* if wild card */
                                type_idx = num_types;
                            else if (rs->req_types[i] >= 0)  /* user type */
                                type_idx = get_type_idx(rs->req_types[i]);
                            else    /* list terminator */
                                break;
                            if (type_idx < 0) aprintf(1,"** invalid type\n");
                            periodic_rq_vector[type_idx]++;
                        }
                        periodic_rq_vector[num_types+1] = rq->count + 1;  /* appending */
                    }
                    rq_append(rq_node);
                    num_reserves_put_on_rq++;
                    if (rfr_to_rank[rs->world_rank] < 0)
                    {
                        for (i=0; i < REQ_TYPE_VECT_SZ; i++)
                        {
                            /* no need to check rfr_rank for this new req from user */
                            if (req_types[i] < -1)  /* invalid type as place-holder */
                                break;
                            cand_rank = find_cand_rank_with_worktype(rs->world_rank,req_types[i]);
                            if (cand_rank >= 0)
                            {
                                aprintf(0000,"REQING rqseqno %d fromrank %d\n",rqseqno,cand_rank);
                                // cblog(1,from_rank,"  REQING from %d ty %d\n",cand_rank,req_types[i]);
                                temp_buf    = amalloc(RFRBUF_NUMINTS * sizeof(int));
                                temp_buf[0] = rqseqno;
                                temp_buf[1] = from_rank;
                                for (j=0; j < REQ_TYPE_VECT_SZ; j++)
                                    temp_buf[2+j] = rs->req_types[j];
                                temp_req    = amalloc(sizeof(MPI_Request));
                                rc = MPI_Isend(temp_buf,RFRBUF_NUMINTS,MPI_INT,cand_rank,SS_RFR,
                                               adlb_all_comm,temp_req);
                                iq_node = iq_node_create(temp_req,(RFRBUF_NUMINTS * sizeof(int)),
                                                         temp_buf);
                                iq_append(iq_node);
                                rfr_to_rank[rs->world_rank] = cand_rank;
                                rfr_out[cand_rank] = 1;
                                nrfrs_sent++;
                                if (use_dbg_prints)
                                    dbg_rfr_sent_cnt[rs->world_rank]++;
                                break;
                            }
                        }
                    }
                }
                else
                {
                    aprintf(0000,"SEND NOCURRWORK TO rank %06d\n",from_rank);
                    info_buf[0] = NO_CURR_WORK;
                    MPI_Send(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_RESERVE_RESP,adlb_all_comm);
                }
            }
            // cblog(1,from_rank,"PAST RESERVE\n");
            aprintf(0000, "PAST FA_RESERVE\n");
            MPE_LOG_EVENT(mpe_svr_reserve_end);
        }
        else if (from_tag == FA_GET_COMMON)
        {
            aprintf(0000, "AT FA_GET_COMMON\n");
            // cblog(1,from_rank,"AT GET_COMMON\n");
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            cq_node = cq_find_seqno(info_buf[0]);
            cs = cq_node->data;
            MPI_Ssend(cs->buf,cs->commlen,MPI_BYTE,from_rank,TA_GET_COMMON_RESP,adlb_all_comm);
            cs->ngets++;
            if (cs->refcnt == cs->ngets)
                cq_delete(cq_node);
        }
        else if (from_tag == FA_GET_RESERVED)
        {
          MPE_LOG_EVENT(mpe_svr_get_start);
            aprintf(0000, "AT FA_GET_RESERVED\n");
            // cblog(1,from_rank,"AT GET_RESERVED\n");
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (using_debug_server)
                num_events_since_logatds++;
            if (no_more_work_flag)
            {
                dbls_info_buf[0] = (double)ADLB_NO_MORE_WORK;
                MPI_Rsend(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,TA_ACK_AND_RC,adlb_all_comm);
                aprintf(0000, "IN FA_GET_RESERVED SENTc NO_MORE_WORK TO %06d\n",from_rank);
                continue;
            }
            wqseqno = info_buf[0];
            wq_node = wq_find_pinned_for_rank(from_rank,wqseqno);
            if ( ! wq_node)
            {
                dbls_info_buf[0] = (double)ERROR;
                MPI_Rsend(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,
                          TA_ACK_AND_RC,adlb_all_comm);
                aprintf(1,"** FAILED GET_RESERVED for rank %06d  wqseqno %d\n",
                        from_rank,info_buf[0]);
                adlb_server_abort(-1,1);
                continue;
            }
            ws = wq_node->data;
            dbls_info_buf[0] = (double) SUCCESS;
            dbls_info_buf[1] = (double) ws->work_len;
            dbls_info_buf[2] = (double) (MPI_Wtime() - ws->time_stamp);
            MPI_Rsend(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,
                      TA_ACK_AND_RC,adlb_all_comm);
            MPI_Ssend(ws->work_buf,ws->work_len,MPI_BYTE,from_rank,
                      TA_GET_RESERVED_RESP,adlb_all_comm);
            if (doing_periodic_stats)
            {
                type_idx = get_type_idx(ws->work_type);
                if (type_idx < 0) aprintf(1,"** invalid type\n");
                if (ws->target_rank >= 0)
                {
                    periodic_wq_2darray[type_idx][ws->target_rank]--;
                }
                else
                {
                    periodic_wq_2darray[type_idx][num_app_ranks]--;
                }
            }
            wq_delete(wq_node);
            update_local_state();
            // cblog(1,from_rank,"PAST GET_RESERVED\n");
            aprintf(0000, "PAST FA_GET_RESERVED\n");
            MPE_LOG_EVENT(mpe_svr_get_end);
        }
        else if (from_tag == FA_NO_MORE_WORK)
        {
            aprintf(0000, "AT FA_NO_MORE_WORK from %06d\n",from_rank);
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (using_debug_server)
                num_events_since_logatds++;
            no_more_work_flag = 1;
            if (my_world_rank == master_server_rank)
            {
                if (num_servers > 1)
                {
                    temp_req = amalloc(sizeof(MPI_Request));
                    rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                    SS_NO_MORE_WORK,adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req,0,NULL);
                    iq_append(iq_node);
                }
            }
            else
            {
                /* just send it on to master server */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Issend(NULL,0,MPI_INT,master_server_rank,
                                SS_NO_MORE_WORK,adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,0,NULL);
                iq_append(iq_node);
            }
            while ((rq_node=xq_first(rq)))
            {
                rs = rq_node->data;
                aprintf(0000,"SENDING NMW to rank %06d\n",rs->world_rank);
                info_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                          TA_RESERVE_RESP,adlb_all_comm);
                aprintf(0000,"SENT NMW to rank %06d\n",rs->world_rank);
                /* since no_more_work, do NOT alter times for total_time_on_rq */
                if (doing_periodic_stats)
                {
                    for (i=0; i < num_types; i++)
                    {
                        if (i == 0  && rs->req_types[i] < 0)  /* if wild card */
                            type_idx = num_types;
                        else if (rs->req_types[i] >= 0)  /* user type */
                            type_idx = get_type_idx(rs->req_types[i]);
                        else    /* list terminator */
                            break;
                        if (type_idx < 0) aprintf(1,"** invalid type\n");
                        periodic_rq_vector[type_idx]--;
                    }
                    periodic_rq_vector[num_types+1] = rq->count - 1; /* deleting */
                    /** not nec when no_more_work
                    type_idx = get_type_idx(rs->req_types[0]);
                    periodic_resolved_reserve_cnt[type_idx]++;
                    **/
                }
                rq_delete(rq_node);
                exhausted_flag = 0;
            }
            aprintf(0000, "PAST FA_NO_MORE_WORK from %06d\n",from_rank);
        }
        else if (from_tag == SS_NO_MORE_WORK)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_NO_MORE_WORK from %06d\n",from_rank);
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (my_world_rank != master_server_rank  ||
                ! no_more_work_flag)  /* I am master, but this is first send by me */
            {
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                SS_NO_MORE_WORK,adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,0,NULL);
                iq_append(iq_node);
            }
            no_more_work_flag = 1;
            while ((rq_node=xq_first(rq)))
            {
                rs = rq_node->data;
                aprintf(0000,"SENDING NMW to rank %06d\n",rs->world_rank);
                info_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                          TA_RESERVE_RESP,adlb_all_comm);
                aprintf(0000,"SENT NMW to rank %06d\n",rs->world_rank);
                /* since no_more_work, do NOT alter times for total_time_on_rq */
                if (doing_periodic_stats)
                {
                    for (i=0; i < num_types; i++)
                    {
                        if (i == 0  && rs->req_types[i] < 0)  /* if wild card */
                            type_idx = num_types;
                        else if (rs->req_types[i] >= 0)  /* user type */
                            type_idx = get_type_idx(rs->req_types[i]);
                        else    /* list terminator */
                            break;
                        if (type_idx < 0) aprintf(1,"** invalid type\n");
                        periodic_rq_vector[type_idx]--;
                    }
                    periodic_rq_vector[num_types+1] = rq->count - 1; /* deleting */
                    /** not nec when no_more_work
                    type_idx = get_type_idx(rs->req_types[0]);
                    periodic_resolved_reserve_cnt[type_idx]++;
                    **/
                }
                rq_delete(rq_node);
                exhausted_flag = 0;
            }
            aprintf(0000, "PAST SS_NO_MORE_WORK from %06d\n",from_rank);
        }
        else if (from_tag == SS_END_LOOP_1)
        {
            num_ss_msgs_handled_since_logatds++;
            /* only arrives on lhs and if there is more than 1 server */
            aprintf(0000, "AT SS_END_LOOP_1\n");
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (my_world_rank == master_server_rank)
            {
                /* change it to loop 2 */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                SS_END_LOOP_2,adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,0,NULL);
                iq_append(iq_node);
            }
            else
            {
                if (num_local_apps_done >= num_apps_this_server)
                {
                    holding_end_loop_1 = 0;
                    temp_req = amalloc(sizeof(MPI_Request));
                    rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                    SS_END_LOOP_1,adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req,0,NULL);
                    iq_append(iq_node);
                }
                else
                    holding_end_loop_1 = 1;
            }
            aprintf(0000, "PAST SS_END_LOOP_1\n");
        }
        else if (from_tag == SS_END_LOOP_2)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_END_LOOP_2 from %06d\n",from_rank);
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            done = 1;
            if (using_debug_server  &&  my_world_rank == master_server_rank)
            {
                rc = MPI_Issend(NULL,0,MPI_INT,debug_server_rank,
                                DS_END,adlb_all_comm,&dummy_req);
            }
            if (my_world_rank != master_server_rank)
            {
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                SS_END_LOOP_2,adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,0,NULL);
                iq_append(iq_node);
            }
            while ((rq_node=xq_first(rq)))
            {
                rs = rq_node->data;
                aprintf(0000,"SENDING NMW to rank %06d\n",rs->world_rank);
                info_buf[0] = ADLB_NO_MORE_WORK;
                MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                          TA_RESERVE_RESP,adlb_all_comm);
                /* since no_more_work, do NOT alter times for total_time_on_rq */
                if (doing_periodic_stats)
                {
                    for (i=0; i < num_types; i++)
                    {
                        if (i == 0  && rs->req_types[i] < 0)  /* if wild card */
                            type_idx = num_types;
                        else if (rs->req_types[i] >= 0)  /* user type */
                            type_idx = get_type_idx(rs->req_types[i]);
                        else    /* list terminator */
                            break;
                        if (type_idx < 0) aprintf(1,"** invalid type\n");
                        periodic_rq_vector[type_idx]--;
                    }
                    periodic_rq_vector[num_types+1] = rq->count - 1; /* deleting */
                    /** not nec when no_more_work
                    type_idx = get_type_idx(ws->work_type);
                    periodic_resolved_reserve_cnt[type_idx]++;
                    **/
                }
                rq_delete(rq_node);
                exhausted_flag = 0;
            }
            aprintf(0000, "PAST SS_END_LOOP_2 from %06d\n",from_rank);
        }
        else if (from_tag == SS_EXHAUST_CHK_LOOP_1)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (my_world_rank == master_server_rank)
            {
                if (rq->count >= num_apps_this_server  &&  exhausted_flag) /* be sure */
                {
                    temp_req = amalloc(sizeof(MPI_Request));
                    MPI_Isend(NULL,0,MPI_INT,rhs_rank,SS_EXHAUST_CHK_LOOP_2,
                              adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, 0, NULL);
                }
            }
            else
            {
                if (rq->count >= num_apps_this_server)
                {
                    exhausted_flag = 1;
                    temp_req = amalloc(sizeof(MPI_Request));
                    MPI_Isend(NULL,0,MPI_INT,rhs_rank,SS_EXHAUST_CHK_LOOP_1,
                              adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, 0, NULL);
                }
            }
        }
        else if (from_tag == SS_EXHAUST_CHK_LOOP_2)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (rq->count >= num_apps_this_server  &&  exhausted_flag) /* be sure */
            {
                if (my_world_rank == master_server_rank)
                {
                    temp_req = amalloc(sizeof(MPI_Request));
                    MPI_Isend(NULL,0,MPI_INT,rhs_rank,SS_DONE_BY_EXHAUSTION,
                              adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, 0, NULL);
                }
                else
                {
                    temp_req = amalloc(sizeof(MPI_Request));
                    MPI_Isend(NULL,0,MPI_INT,rhs_rank,SS_EXHAUST_CHK_LOOP_2,
                              adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, 0, NULL);
                }
            }
        }
        else if (from_tag == SS_DONE_BY_EXHAUSTION)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(NULL,0,MPI_INT,from_rank,from_tag,adlb_all_comm,&status);
            if (my_world_rank != master_server_rank)
            {
                temp_req = amalloc(sizeof(MPI_Request));
                MPI_Isend(NULL,0,MPI_INT,rhs_rank,SS_DONE_BY_EXHAUSTION,
                          adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req, 0, NULL);
            }
            while ((rq_node=xq_first(rq)))
            {
                rs = rq_node->data;
                info_buf[0] = ADLB_DONE_BY_EXHAUSTION;
                MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                          TA_RESERVE_RESP,adlb_all_comm);
                /* since exhaustion, do NOT alter times for total_time_on_rq */
                rq_delete(rq_node);
                /* exhausted_flag = 0; */  /* leave it set here */
                /* done, so not dealing with periodic stats right now */
            }
        }
        else if (from_tag == SS_DBG_TIMING_MSG)  /* only sent when use_dbg_prints = 1 */
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,from_tag,
                     adlb_all_comm,&status);
            if (my_world_rank == master_server_rank)
            {
                prev_dbg_msg_timelen = MPI_Wtime() - prev_dbg_msg_start;
                aprintf(1111,"DBG4: %f %f\n",prev_dbg_msg_timelen,dbg_prev_qmstat_timelen);
                dbg_msg_is_out = 0;
            }
            else
            {
#               ifdef DEBUGGING_BGP
                k = GetUnexpectedRequestCount();
#               else
#               ifdef DEBUGGING_SICORTEX
                k = MPIDI_Debug_early_queue_length();
#               else
                k = 0;
#               endif
#               endif
                if ((MPI_Wtime() - dbls_info_buf[1]) > 1.0)
                {
                    temp_dbl = MPI_Wtime();
                    /* print loop time so far, hop time, unexpected queue count */
                    sprintf(dbg_print_buf,"%f %f %d ",
                            temp_dbl-dbls_info_buf[0],temp_dbl-dbls_info_buf[1],k);
#                   ifdef DEBUGGING_BGP
                    strcat(dbg_print_buf," ; ");
                    GetUnexpectedRequestTagsInDBGTagsBuf(dbg_unexpected_by_tag);
                    for (i=0; i < 50; i++)
                    {
                        if (dbg_unexpected_by_tag[i] > 0)
                        {
                            sprintf(dbg_temp_buf,"%d:%d ",i+1000,dbg_unexpected_by_tag[i]);
                            strcat(dbg_print_buf,dbg_temp_buf);
                        }
                    }
                    for (i=0; i < 50; i++)
                        dbg_unexpected_by_tag[i] = 0;
#                   endif
                    aprintf(1111,"DBG7: %s\n",dbg_print_buf);
                }
                dbls_temp_buf    = amalloc(IBUF_NUMDBLS * sizeof(double));
                dbls_temp_buf[0] = dbls_info_buf[0];  /* loop start time */
                dbls_temp_buf[1] = MPI_Wtime();       /* hop  start time */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Isend(dbls_temp_buf,IBUF_NUMDBLS,MPI_DOUBLE,rhs_rank,SS_DBG_TIMING_MSG,
                               adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req, IBUF_NUMDBLS*sizeof(double), dbls_temp_buf);
                iq_append(iq_node);
            }
        }
        else if (from_tag == SS_QMSTAT)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_QMSTAT from %06d\n",from_rank);
            /****
            qmstat_buf = amalloc(qmstat_buflen);
            MPI_Recv(qmstat_buf,qmstat_buflen,MPI_PACKED,from_rank,
                     SS_QMSTAT,adlb_all_comm,&status);
            ****/
            nqmstatmsgs++;
            update_local_state();  // perhaps not nec here
            /* backup my entry */
            server_idx = get_server_idx(my_world_rank);
            temp_qm.qlen_unpin_untarg = qmstat_tbl[server_idx].qlen_unpin_untarg;
            temp_qm.nbytes_used   = qmstat_tbl[server_idx].nbytes_used;
            for (i=0; i < num_types; i++)
                temp_qm.type_hi_prio[i] = qmstat_tbl[server_idx].type_hi_prio[i];
            /* unpack */
            unpack_qmstat();  /* from qmstat_recv_buf into qmstat_tbl */
            /* restore my entry */
            qmstat_tbl[server_idx].qlen_unpin_untarg = temp_qm.qlen_unpin_untarg;
            qmstat_tbl[server_idx].nbytes_used = temp_qm.nbytes_used;
            for (i=0; i < num_types; i++)
                qmstat_tbl[server_idx].type_hi_prio[i] = temp_qm.type_hi_prio[i];
            /* */
            // dump_qmstat_info();
            if (my_world_rank == master_server_rank)
            {
                temp_dbl = MPI_Wtime() - prev_qmstat_msg_time;
                dbg_prev_qmstat_timelen = temp_dbl;
                if (temp_dbl > 5.0)
                    aprintf(1,"one long qmstat trip time was %f\n",temp_dbl);
                if (temp_dbl > qmstat_interval)
                    num_qmstats_exceeded_interval++;
                sum_of_qmstat_trip_times += temp_dbl;
                if (temp_dbl > max_qmstat_trip_time)
                    max_qmstat_trip_time = temp_dbl;
                qmstat_msg_is_out = 0;
            }
            else
            {
                qmstat_send_buf = amalloc(qmstat_buflen);
                pack_qmstat();    /* pack ALL info from qmstat_tbl into qmstat_send_buf */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Isend(qmstat_send_buf,qmstat_buflen,MPI_PACKED,rhs_rank,SS_QMSTAT,
                               adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,qmstat_buflen,qmstat_send_buf);
                iq_append(iq_node);
                // qmstat_isend_node_ptr = iq_node;
            }
            check_remote_work_for_queued_apps();
            aprintf(0000, "PAST SS_QMSTAT\n");
        }
        else if (from_tag == FA_LOCAL_APP_DONE)
        {
            aprintf(0000, "AT FA_LOCAL_APP_DONE from %06d\n",from_rank);
            MPI_Recv(NULL,0,MPI_INT,from_rank,FA_LOCAL_APP_DONE,adlb_all_comm,&status);
            if (using_debug_server)
                num_events_since_logatds++;
            num_local_apps_done++;
            if (num_local_apps_done >= num_apps_this_server)
            {
                if (my_world_rank == master_server_rank)
                {
                    if (num_servers == 1)
                    {
                        done = 1;
                        if (using_debug_server)
                        {
                            rc = MPI_Issend(NULL,0,MPI_INT,debug_server_rank,
                                            DS_END,adlb_all_comm,&dummy_req);
                        }
                    }
                    else
                    {
                        holding_end_loop_1 = 0;
                        temp_req = amalloc(sizeof(MPI_Request));
                        rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                        SS_END_LOOP_1,adlb_all_comm,temp_req);
                        iq_node = iq_node_create(temp_req,0,NULL);
                        iq_append(iq_node);
                    }
                }
                else
                {
                    if (holding_end_loop_1)
                    {
                        holding_end_loop_1 = 0;
                        temp_req = amalloc(sizeof(MPI_Request));
                        rc = MPI_Issend(NULL,0,MPI_INT,rhs_rank,
                                        SS_END_LOOP_1,adlb_all_comm,temp_req);
                        iq_node = iq_node_create(temp_req,0,NULL);
                        iq_append(iq_node);
                    }
                }
            }
        }
        else if (from_tag == SS_RFR)
        {
            nrfrs_recvd++;
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(rfr_buf,RFRBUF_NUMINTS,MPI_INT,from_rank,SS_RFR,adlb_all_comm,&status);
            orig_rqseqno = rfr_buf[0];
            for_rank     = rfr_buf[1];  // new for PTW immediately below
            for (j=0; j < REQ_TYPE_VECT_SZ; j++)
                req_types[j] = rfr_buf[2+j];
            if (use_dbg_prints)
                for (j=0; j < REQ_TYPE_VECT_SZ; j++)
                    if (req_types[j] >= 0)
                        dbg_rfr_attempts_by_type[j]++;
            // aprintf(0000, "AT SS_RFR from %d rqseqno %d\n",from_rank,orig_rqseqno);
            // PTW:  now need to check for targeted on this remote system as well
            wq_node = wq_find_pre_targeted_hi_prio(for_rank,req_types);
            if ( ! wq_node)
                wq_node = wq_find_hi_prio(req_types);  /* does NOT find targeted */
            // aprintf(0000,"SS_RFR from %d  for %d  wqnode %p\n",from_rank,for_rank,wq_node);
            if (wq_node)
            {
                ws = wq_node->data;
                prev_target = ws->target_rank;  // new with PTW
                ws->pin_rank = for_rank;
                if (ws->pin_rank >= 0)
                    ws->pinned = 1;
                temp_buf    = amalloc(RFRBUF_NUMINTS * sizeof(int));
                temp_buf[0] = SUCCESS;
                temp_buf[1] = orig_rqseqno;
                temp_buf[2] = for_rank;
                temp_buf[3] = ws->work_type;
                temp_buf[4] = ws->work_prio;
                temp_buf[5] = ws->work_len;
                temp_buf[6] = ws->answer_rank;
                temp_buf[7] = ws->wqseqno;
                temp_buf[8] = prev_target;  /* new with PTW */
                temp_buf[9]  = ws->common_len;
                temp_buf[10] = ws->common_server_rank;
                temp_buf[11] = ws->common_server_commseqno;
                temp_req = amalloc(sizeof(MPI_Request));
                aprintf(0000,"SENDING RFR_RESP to %d wqseqno %d\n",from_rank,ws->wqseqno);
                MPI_Isend(temp_buf,RFRBUF_NUMINTS,MPI_INT,from_rank,SS_RFR_RESP,
                          adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,(RFRBUF_NUMINTS * sizeof(int)),temp_buf);
                iq_append(iq_node);
            }
            else
            {
                temp_buf    = amalloc(RFRBUF_NUMINTS * sizeof(int));
                temp_buf[0] = NO_CURR_WORK;
                temp_buf[1] = orig_rqseqno;
                temp_buf[2] = for_rank;
                for (j=0; j < REQ_TYPE_VECT_SZ; j++)
                    temp_buf[3+j] = rfr_buf[2+j];
                temp_req    = amalloc(sizeof(MPI_Request));
                aprintf(0000,"SENDING RFR_RESP to rank %06d  rc -2\n",from_rank);
                MPI_Isend(temp_buf,RFRBUF_NUMINTS,MPI_INT,from_rank,SS_RFR_RESP,
                          adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req, (RFRBUF_NUMINTS * sizeof(int)),temp_buf);
                iq_append(iq_node);
                /* assume I previously had work that they are seeking and send an update */
                update_local_state();
            }
            aprintf(0000, "PAST SS_RFR from %06d found_wqnode %p\n",from_rank,wq_node);
        }
        else if (from_tag == SS_RFR_RESP)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(rfr_buf,RFRBUF_NUMINTS,MPI_INT,from_rank,SS_RFR_RESP,
                     adlb_all_comm,&status);
            rc           = rfr_buf[0];
            orig_rqseqno = rfr_buf[1];
            for_rank     = rfr_buf[2];
            // cblog(1,info_buf[3],"  RFR_RESP for me from %d ty %d rc %d\n",
                  // from_rank,orig_req_type,rc);
            rfr_to_rank[for_rank] = -1;  /* no longer has an outstanding rfr */
            rfr_out[from_rank] = 0;
            if (rc == SUCCESS)
            {
                aprintf(0000, "AT SS_RFR_RESP from %d for %d rqseqno %d\n",
                        from_rank,for_rank,orig_rqseqno);
                rq_node = rq_find_seqno(orig_rqseqno);
                if (rq_node)
                {
                    rs = rq_node->data;
                    /* CAREFULLY move values up in info_buf */
                    info_buf[0] = SUCCESS;
                    info_buf[1] = rfr_buf[3];  /* work_type */
                    info_buf[2] = rfr_buf[4];  /* work_prio */
                    info_buf[3] = rfr_buf[5];  /* work_len */
                    info_buf[4] = rfr_buf[6];  /* answer_rank */
                    info_buf[5] = rfr_buf[7];  /* wqseqno */
                    info_buf[6] = from_rank;
                    /* rfr_buf[8]  (prev_target) is used below */
                    info_buf[7] = rfr_buf[9];  /* common_len */
                    info_buf[8] = rfr_buf[10]; /* common_server_rank */
                    info_buf[9] = rfr_buf[11]; /* common_server_commseqno */
                    aprintf(0000,"SS_RFR_RESP: SENDING RESERVATION to rank %06d\n",rs->world_rank);
                    MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                              TA_RESERVE_RESP,adlb_all_comm);
                    if (use_dbg_prints  &&  (MPI_Wtime() - rs->time_stamp) > DBG_CHECK_TIME)
                    {
                        aprintf(0000,"DBG3: rfr %d %f -1.0 %d %d\n",
                                rs->rqseqno,MPI_Wtime()-rs->time_stamp,
                                rs->world_rank,info_buf[1]);
                    }
                    if (first_time_on_rq[rs->world_rank])
                        first_time_on_rq[rs->world_rank] = 0;
                    else
                    {
                        total_time_on_rq += (MPI_Wtime() - rs->time_stamp);
                        num_rq_nodes_timed++;
                    }
                    if (doing_periodic_stats)
                    {
                        for (i=0; i < num_types; i++)
                        {
                            if (i == 0  && rs->req_types[i] < 0)  /* if wild card */
                                type_idx = num_types;
                            else if (rs->req_types[i] >= 0)  /* user type */
                                type_idx = get_type_idx(rs->req_types[i]);
                            else    /* list terminator */
                                break;
                            if (type_idx < 0) aprintf(1,"** invalid type\n");
                            periodic_rq_vector[type_idx]--;
                        }
                        periodic_rq_vector[num_types+1] = rq->count - 1; /* deleting */
                        type_idx = get_type_idx(rfr_buf[3]);
                        if (type_idx < 0) aprintf(1,"** invalid type\n");
                        periodic_resolved_reserve_cnt[type_idx]++;
                    }
                    rq_delete(rq_node);
                    exhausted_flag = 0;
                    if (for_rank == rfr_buf[8])  /* if for_rank is also target rank */
                    {
                        tq_node = tq_find_rtr(for_rank,rfr_buf[3],from_rank);
                        if (tq_node)
                        {
                            ts = tq_node->data;
                            ts->num_stored--;
                            if (ts->num_stored <= 0)
                            {
                                tq_delete(tq_node);
                            }
                        }
                    }
                }
                else
                {
                    /* OK; a PUT may have caused this rqseqno to be deleted earlier */
                    /*  but, now need to un-reserve at remote server */
                    temp_buf    = amalloc(IBUF_NUMINTS * sizeof(int));
                    temp_buf[0] = for_rank;     /* reserved-for rank */
                    temp_buf[1] = rfr_buf[7];  /* wqseqno on remote server */
                    temp_buf[2] = rfr_buf[8];  /* prev_target on remote host */  // new with PTW
                    temp_req    = amalloc(sizeof(MPI_Request));
                    aprintf(0000,"SENDING UNRESERVE to %06d  forrank %d wqseqno %d\n",from_rank,rfr_buf[3],rfr_buf[8]);
                    MPI_Isend(temp_buf,IBUF_NUMINTS,MPI_INT,from_rank,SS_UNRESERVE,
                              adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, (IBUF_NUMINTS * sizeof(int)),temp_buf);
                    iq_append(iq_node);
                }
                check_remote_work_for_queued_apps();  /* may do another rfr for for_rank */
            }
            else
            {
                aprintf(0000,"RECVD SS_RFR_RESP from %06d rc %d\n",from_rank,rc);
                if (using_debug_server)
                    num_rfr_failed_since_logatds++;
                server_idx  = get_server_idx(from_rank);
                /* setup to patch status vector and tq; if wildcard, do all types */
                if (rfr_buf[3] < 0)  /* if wild card */
                {
                    for (i=0; i < num_types; i++)
                        rfr_buf[3+i] = user_types[i];
                    rfr_buf[3+i] = -1;
                }
                /* patch status vector and tq */
                for (i=3; i < 3+REQ_TYPE_VECT_SZ && rfr_buf[i] >= 0; i++)
                {
                    /* patch status vector */
                    type_idx = get_type_idx(rfr_buf[i]);
                    if (type_idx < 0) aprintf(1,"** invalid type\n");
                    qmstat_tbl[server_idx].type_hi_prio[type_idx] = ADLB_LOWEST_PRIO;

                    /* patch tq if nec */
                    for (tq_node=xq_first(tq); tq_node; tq_node=xq_next(tq,tq_node))
                    {
                        ts = tq_node->data;
                        if (ts->app_rank == for_rank             &&
                            ts->remote_server_rank == from_rank  &&
                            ts->work_type == rfr_buf[i])
                        {
                            ts->num_stored--; /* can NOT delete now; do below */
                            if (ts->num_stored <= 0)
                            {
                                tq_prev = tq_node->prev;
                                tq_delete(tq_node);
                                tq_node = tq_prev;
                            }
                            num_tq_nodes_fixed++;
                        }
                    }
                }

                rq_node = rq_find_seqno(orig_rqseqno);
                if (rq_node)
                {
                    rs = rq_node->data;  /* grab it again */
                    for (i=0; i < REQ_TYPE_VECT_SZ; i++)
                    {
                        if (rs->req_types[i] < -1)  /* invalid type as place-holder */
                            break;
                        cand_rank = find_cand_rank_with_worktype(rs->world_rank,rs->req_types[i]);
                        if (cand_rank >= 0)
                        {
                            temp_buf    = amalloc(RFRBUF_NUMINTS * sizeof(int));
                            temp_buf[0] = orig_rqseqno;
                            temp_buf[1] = rs->world_rank;
                            for (j=0; j < REQ_TYPE_VECT_SZ; j++)
                                temp_buf[2+j] = rs->req_types[j];
                            temp_req    = amalloc(sizeof(MPI_Request));
                            aprintf(0000,"REQING from alt rqseqno %d fromrank %d\n",
                                    orig_rqseqno,cand_rank);
                            // cblog(1,rs->world_rank,"  REQING from alt %d ty %d\n",
                                  // cand_rank,rs->req_types[i]);
                            MPI_Isend(temp_buf,RFRBUF_NUMINTS,MPI_INT,cand_rank,SS_RFR,
                                      adlb_all_comm,temp_req);
                            iq_node = iq_node_create(temp_req,(RFRBUF_NUMINTS * sizeof(int)),
                                                     temp_buf);
                            iq_append(iq_node);
                            rfr_to_rank[rs->world_rank] = cand_rank;
                            rfr_out[cand_rank] = 1;
                            nrfrs_sent++;
                            if (use_dbg_prints)
                                dbg_rfr_sent_cnt[rs->world_rank]++;
                            break;
                        }
                    }
                }
                else
                {
                    /* OK; a PUT may have caused this rqseqno to be deleted earlier */
                    /* aprintf(1,"** INVALID STATE; rqseqno %d not found\n",orig_rqseqno); */
                }
                check_remote_work_for_queued_apps();  /* may do another rfr for for_rank */
            }
            aprintf(0000, "PAST SS_RFR_RESP\n");
        }
        else if (from_tag == SS_UNRESERVE)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,SS_UNRESERVE,
                     adlb_all_comm,&status);
            aprintf(0000, "AT UNRESERVE from %06d  rrank %d wqseqno %d\n",from_rank,info_buf[0],info_buf[1]);
            wq_node = wq_find_pinned_for_rank(info_buf[0],info_buf[1]);  /* rank,wqseqno */
            if (wq_node)
            {
                ws = wq_node->data;
                ws->pin_rank = info_buf[2];  /* may be -1 */
                ws->pinned = 0; // PTW: UNcomment to support pushing targeted work
            }
            else
            {
                aprintf(1, "** UNRESERVE did not find rank %d wqseqno %d from %06d\n",
                        info_buf[0],info_buf[1],from_rank);
            }
            aprintf(0000, "PAST UNRESERVE from %06d\n",from_rank);
        }
        else if (from_tag == SS_MOVING_TARGETED_WORK)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT MOVING_TARGETED_WORK from %d\n",from_rank);
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,SS_MOVING_TARGETED_WORK,
                     adlb_all_comm,&status);
            tq_node = tq_find_rtr(info_buf[0],info_buf[1],info_buf[2]);
            if (tq_node)
            {
                ts = tq_node->data;
                ts->num_stored--;
                if (ts->num_stored <= 0)
                    tq_delete(tq_node);
            }
            else
            {
                /* this is OK; it just means home_server did the push */
                aprintf(0,"** couldn't find tq record: %d %d %d ; %d\n",
                        info_buf[0],info_buf[1],info_buf[2],info_buf[3]);
            }
            if (info_buf[3] != my_world_rank)  /* if not put in my wq, update my tq */
            {
                tq_node = tq_find_rtr(info_buf[0],info_buf[1],info_buf[3]);
                if (tq_node)
                {
                    ts = tq_node->data;
                    ts->num_stored++;
                }
                else
                {
                    tq_node = tq_node_create(info_buf[0],info_buf[1],info_buf[3],1);
                    tq_append(tq_node);
                    ts = tq_node->data;
                }
            }
            check_remote_work_for_queued_apps();  /* make sure no one is waiting for this */
            aprintf(0000, "PAST MOVING_TARGETED_WORK from %06d\n",from_rank);
        }
        else if (from_tag == SS_PUSH_QUERY)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_PUSH_QUERY from %06d\n",from_rank);
            MPI_Recv(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,SS_PUSH_QUERY,
                     adlb_all_comm,&status);
            work_type    = (int) dbls_info_buf[0];
            work_prio    = (int) dbls_info_buf[1];
            work_len     = (int) dbls_info_buf[2];
            answer_rank  = (int) dbls_info_buf[3];
            /* remaining dbls_info_buf[x] used below */

            dbls_temp_buf = amalloc(IBUF_NUMDBLS * sizeof(double));
            if ((curr_bytes_dmalloced+work_len) >= THRESHOLD_TO_START_PUSH)
            {
                dbls_temp_buf[0] = (double) -1;
                dbls_temp_buf[1] = curr_bytes_dmalloced;
                dbls_temp_buf[2] = dbls_info_buf[7];  /* seqno on pusher */
                dbls_temp_buf[3] = next_wqseqno;      /* seqno it will have here */
                temp_req = amalloc(sizeof(MPI_Request));
                rc = MPI_Isend(dbls_temp_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,
                               SS_PUSH_QUERY_RESP,adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req, IBUF_NUMDBLS*sizeof(double), dbls_temp_buf);
                iq_append(iq_node);
                continue;
            }

            dbls_temp_buf[0] = (double) my_world_rank;
            dbls_temp_buf[1] = curr_bytes_dmalloced;
            dbls_temp_buf[2] = dbls_info_buf[7];  /* seqno on pusher */
            dbls_temp_buf[3] = next_wqseqno;      /* seqno it will have here */
            temp_req = amalloc(sizeof(MPI_Request));
            rc = MPI_Isend(dbls_temp_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,
                           SS_PUSH_QUERY_RESP,adlb_all_comm,temp_req);
            iq_node = iq_node_create(temp_req, IBUF_NUMDBLS*sizeof(double), dbls_temp_buf);
            iq_append(iq_node);

            work_buf                    = amalloc(work_len);
            wq_node = wq_node_create(work_type,work_prio,next_wqseqno++,answer_rank,
                                     my_world_rank,work_len,work_buf);  /* target is me for now */
            ws = wq_node->data;
            ws->time_stamp              = dbls_info_buf[4];
            ws->target_rank             = my_world_rank;  /* reserve for me until push_hdr */
            ws->temp_target_rank        = (int) dbls_info_buf[5];
            ws->home_server_rank        = (int) dbls_info_buf[6];
            /* ws->wqseqno                 = (int) dbls_info_buf[7]; */ /* use local wqseqno */
            ws->common_len              = (int) dbls_info_buf[8];
            ws->common_server_rank      = (int) dbls_info_buf[9];
            ws->common_server_commseqno = (int) dbls_info_buf[10];
            ws->pin_rank                = my_world_rank;  /* pin for myself until push */
            ws->pinned                  = 1;              /* */
            wq_append(wq_node);
        }
        else if (from_tag == SS_PUSH_QUERY_RESP)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_rank,SS_PUSH_QUERY_RESP,
                     adlb_all_comm,&status);
            to_rank = (int) dbls_info_buf[0];
            server_idx = get_server_idx(from_rank);
            qmstat_tbl[server_idx].nbytes_used = dbls_info_buf[1];
            push_query_is_out = 0;
            if (to_rank < 0)
                continue;
            if (push_attempt_cntr >= MAX_PUSH_ATTEMPTS && (MPI_Wtime()-job_start_time) > 30)
            {
                aprintf(1,"** adlb_server: push succeeded after %d attempts\n",
                        push_attempt_cntr);
            }
            push_attempt_cntr = 0;
            wq_node = wq_find_seqno( (int) dbls_info_buf[2] );
            if (wq_node)
                ws = wq_node->data;
            if ( ! wq_node  ||  ws->pinned)  /* may have been Reserved or retrieved via Get */
            {
                temp_buf = amalloc(IBUF_NUMINTS * sizeof(int));
                temp_buf[0] = (int) dbls_info_buf[3];  /* wqseqno on pushee */
                temp_req = amalloc(sizeof(MPI_Request));
                MPI_Isend(temp_buf,IBUF_NUMINTS,MPI_INT,to_rank,SS_PUSH_DEL,
                          adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,(IBUF_NUMINTS * sizeof(int)),temp_buf);
                iq_append(iq_node);
                continue;
            }

            temp_buf = amalloc(IBUF_NUMINTS * sizeof(int));
            temp_buf[0] = (int) dbls_info_buf[3];  /* wqseqno on pushee */
            temp_req = amalloc(sizeof(MPI_Request));
            MPI_Isend(temp_buf,IBUF_NUMINTS,MPI_INT,to_rank,SS_PUSH_HDR,
                      adlb_all_comm,temp_req);
            iq_node = iq_node_create(temp_req,(IBUF_NUMINTS * sizeof(int)),temp_buf);
            iq_append(iq_node);

            work_len = ws->work_len;
            work_buf = amalloc(work_len);
            memcpy(work_buf,ws->work_buf,work_len);
            temp_req = amalloc(sizeof(MPI_Request));
            MPI_Isend(work_buf,work_len,MPI_BYTE,to_rank,SS_PUSH_WORK,adlb_all_comm,temp_req);
            iq_node = iq_node_create(temp_req,work_len,work_buf);
            iq_append(iq_node);
            if (doing_periodic_stats)
            {
                type_idx = get_type_idx(ws->work_type);
                if (type_idx < 0) aprintf(1,"** invalid type\n");
                if (ws->target_rank >= 0)
                {
                    periodic_wq_2darray[type_idx][ws->target_rank]--;
                }
                else
                {
                    periodic_wq_2darray[type_idx][num_app_ranks]--;
                }
            }
            wq_delete(wq_node);
            npushed_from_here++;
            update_local_state();
        }
        else if (from_tag == SS_PUSH_HDR)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_PUSH_HDR from %06d\n",from_rank);
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,SS_PUSH_HDR,
                     adlb_all_comm,&status);
            wq_node = wq_find_seqno(info_buf[0]);
            if ( ! wq_node)
            {
                aprintf(1,"** aborting: invalid push_hdr from %d  wqseqno %d\n",
                        from_rank,info_buf[0]);
                adlb_server_abort(-1,1);
            }
            ws = wq_node->data;
            ws->target_rank = ws->temp_target_rank;  /* switch back to real target now */
            ws->pin_rank = -1;  /* no longer pinned here */
            ws->pinned   = 0;
            MPI_Recv(ws->work_buf,ws->work_len,MPI_BYTE,from_rank,SS_PUSH_WORK,
                     adlb_all_comm,&status);
            npushed_to_here++;
            if (ws->target_rank >= 0)
            {
                if (ws->home_server_rank == my_world_rank)
                {
                    tq_node = tq_find_rtr(ws->target_rank,ws->work_type,from_rank);
                    if (tq_node)
                    {
                        ts = tq_node->data;
                        ts->num_stored--;
                        if (ts->num_stored <= 0)
                            tq_delete(tq_node);
                    }
                }
                else
                {
                    temp_buf    = amalloc(IBUF_NUMINTS * sizeof(int));
                    temp_buf[0] = ws->target_rank;
                    temp_buf[1] = ws->work_type;
                    temp_buf[2] = from_rank;      /* data moved from server */
                    temp_buf[3] = my_world_rank;  /* data moved to server */
                    temp_req    = amalloc(sizeof(MPI_Request));
                    rc = MPI_Isend(temp_buf,IBUF_NUMINTS,MPI_INT,ws->home_server_rank,
                                   SS_MOVING_TARGETED_WORK,adlb_all_comm,temp_req);
                    iq_node = iq_node_create(temp_req, (IBUF_NUMINTS * sizeof(int)) ,temp_buf);
                    iq_append(iq_node);
                }
            }
            if (doing_periodic_stats)
            {
                type_idx = get_type_idx(ws->work_type);
                if (type_idx < 0) aprintf(1,"** invalid type\n");
                if (ws->target_rank >= 0)
                {
                    periodic_wq_2darray[type_idx][ws->target_rank]++;
                }
                else
                {
                    periodic_wq_2darray[type_idx][num_app_ranks]++;
                }
            }
            // target_rank = -1;  //PTW: targeted is NOW pushed /* targeted work is not pushed */
            rq_node = rq_find_rank_queued_for_type(ws->target_rank,ws->work_type);
            if (rq_node)
            {
                rs = rq_node->data;
                ws->pin_rank = rs->world_rank;
                if (ws->pin_rank >= 0)
                    ws->pinned = 1;
                info_buf[0] = SUCCESS;
                info_buf[1] = ws->work_type;
                info_buf[2] = ws->work_prio;
                info_buf[3] = ws->work_len;
                info_buf[4] = ws->answer_rank;
                info_buf[5] = ws->wqseqno;
                info_buf[6] = my_world_rank;
                info_buf[7] = ws->common_len;
                info_buf[8] = ws->common_server_rank;
                info_buf[9] = ws->common_server_commseqno;
                aprintf(0000,"IN SS_PUSH_HDR SENDING RESERVATION to rank %06d\n",rs->world_rank);
                MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
                          TA_RESERVE_RESP,adlb_all_comm);
                if (use_dbg_prints  &&  (MPI_Wtime() - rs->time_stamp) > DBG_CHECK_TIME)
                {
                    aprintf(0000,"DBG3: psh %d %f %f %d %d\n",
                            rs->rqseqno,MPI_Wtime()-rs->time_stamp,
                            MPI_Wtime()-ws->time_stamp,rs->world_rank,ws->work_type);
                }
                if (first_time_on_rq[rs->world_rank])
                    first_time_on_rq[rs->world_rank] = 0;
                else
                {
                    total_time_on_rq += (MPI_Wtime() - rs->time_stamp);
                    num_rq_nodes_timed++;
                }
                if (doing_periodic_stats)
                {
                    for (i=0; i < num_types; i++)
                    {
                        if (i == 0  && rs->req_types[i] < 0)  /* if wild card */
                            type_idx = num_types;
                        else if (rs->req_types[i] >= 0)  /* user type */
                            type_idx = get_type_idx(rs->req_types[i]);
                        else    /* list terminator */
                            break;
                        if (type_idx < 0) aprintf(1,"** invalid type\n");
                        periodic_rq_vector[type_idx]--;
                    }
                    periodic_rq_vector[num_types+1] = rq->count - 1; /* deleting */
                    type_idx = get_type_idx(ws->work_type);
                    if (type_idx < 0) aprintf(1,"** invalid type\n");
                    periodic_resolved_reserve_cnt[type_idx]++;
                }
                rq_delete(rq_node);
                exhausted_flag = 0;
            }
            else
            {
                update_local_state();
            }
            aprintf(0000, "PAST SS_PUSH_HDR from %06d\n",from_rank);
        }
        else if (from_tag == SS_PUSH_DEL)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_PUSH_DEL from %06d\n",from_rank);
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,SS_PUSH_DEL,
                     adlb_all_comm,&status);
            wq_node = wq_find_seqno(info_buf[0]);
            if ( ! wq_node)
            {
                aprintf(1,"** aborting: invalid push_del from %d  wqseqno %d\n",
                        from_rank,info_buf[0]);
                adlb_server_abort(-1,1);
            }
            wq_delete(wq_node);
            /* no need to update state here; actual push never occurred */
        }
        else if (from_tag == FA_ADLB_ABORT)
        {
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,FA_ADLB_ABORT,
                     adlb_all_comm,&status);
            aprintf(1,"** adlb_server: recvd abort %d from app %06d\n",
                    info_buf[0],from_rank);
            adlb_server_abort(info_buf[0],0);  /* do not call mpi_abort; client will do it */
            aprintf(0000, "PAST FA_ADLB_ABORT from %06d\n",from_rank);
        }
        else if (from_tag == FA_LOG)
        {
            MPI_Recv(log_buf,100,MPI_BYTE,from_rank,FA_LOG,adlb_all_comm,&status);
            // cblog(1,from_rank,log_buf);
        }
        else if (from_tag == SS_ADLB_ABORT)
        {
            num_ss_msgs_handled_since_logatds++;
            aprintf(0000, "AT SS_ADLB_ABORT from %06d\n",from_rank);
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,SS_ADLB_ABORT,
                     adlb_all_comm,&status);
            aprintf(1,"** adlb_server: HANDLING ADLB_ABORT from server %06d\n",from_rank);
            print_final_stats();
            MPI_Isend(info_buf,IBUF_NUMINTS,MPI_INT,rhs_rank,SS_ADLB_ABORT,
                      adlb_all_comm,&dummy_req);
            aprintf(0000, "PAST SS_ADLB_ABORT from %06d\n",from_rank);
            sleep(1);
            /* only after servers have all reacted */
            MPI_Abort(MPI_COMM_WORLD,info_buf[0]);
        }
        else if (from_tag == SS_PERIODIC_STATS)
        {
            num_ss_msgs_handled_since_logatds++;
            MPI_Recv(periodic_buf,periodic_buf_num_ints,MPI_INT,from_rank,SS_PERIODIC_STATS,
                     adlb_all_comm,&status);
            if (my_world_rank == master_server_rank)
            {
                temp_char_buf = amalloc(periodic_buf_num_ints * 9);  /* assume int < 9 chars */
                temp_char_buf[0] = '\0';
                for (i=0; i < periodic_buf_num_ints; i++)
                {
                    sprintf(temp_str,"%d ",periodic_buf[i]);
                    strcat(temp_char_buf,temp_str);
                }
                nbytes_printed = 0;
                nbytes_left_to_print = strlen(temp_char_buf);
                for (i=0; nbytes_left_to_print > 0; i++)
                {
                    if (nbytes_left_to_print > 500)  /* also a header on each line */
                        k = 500;
                    else
                        k = nbytes_left_to_print;
                    memcpy(buf1000,temp_char_buf+nbytes_printed,k);
                    buf1000[k] = '\0';
                    aprintf(1,"STAT_APS: lct=%d: %s\n",i,buf1000);
                    nbytes_printed += k;
                    nbytes_left_to_print -= k;
                }
                afree(temp_char_buf,periodic_buf_num_ints * 16);
            }
            else
            {
                temp_buf = amalloc(periodic_buf_num_ints * sizeof(int));
                temp_req = amalloc(sizeof(MPI_Request));
                /* put in wq_2darray stuff */
                for (i=0; i < num_types; i++)
                {
                    for (j=0; j < (num_app_ranks+1); j++)
                    {
                        k = (i * (num_app_ranks+1)) + j;
                        temp_buf[k] = periodic_buf[k] + periodic_wq_2darray[i][j];
                    }
                }
                /* put in rq_vector stuff */
                skip = (num_app_ranks + 1) * num_types;  /* skip wq_2d */
                for (i=0; i < (num_types+2); i++)
                {
                    k = i + skip;
                    temp_buf[k] = periodic_buf[k] + periodic_rq_vector[i];
                }
                /* put in put_cnt stuff */
                skip += num_types + 2;
                for (i=0; i < num_types; i++)
                {
                    k = i + skip;
                    temp_buf[k] = periodic_buf[k] + periodic_put_cnt[i];
                }
                /* put in resolved_reserve stuff */
                skip += num_types;
                for (i=0; i < num_types; i++)
                {
                    k = i + skip;
                    temp_buf[k] = periodic_buf[k] + periodic_resolved_reserve_cnt[i];
                }
                MPI_Isend(temp_buf,periodic_buf_num_ints,MPI_INT,rhs_rank,
                          SS_PERIODIC_STATS,adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,periodic_buf_num_ints*sizeof(int),temp_buf);
                iq_append(iq_node);
            }
            for (i=0; i < num_types; i++)
            {
                periodic_put_cnt[i] = 0;
                periodic_resolved_reserve_cnt[i] = 0;
            }
        }
        else if (from_tag == FA_INFO_NUM_WORK_UNITS)
        {
            MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,FA_INFO_NUM_WORK_UNITS,
                     adlb_all_comm,&status);
            work_type = info_buf[0];
            info_buf[0] = ADLB_LOWEST_PRIO;    /* max prio of that type */
            info_buf[1] = 0;                   /* num of that type AND max prio */
            info_buf[2] = 0;                   /* num total of that type */
            if (no_more_work_flag)
                info_buf[3] = ADLB_NO_MORE_WORK;
            else
                info_buf[3] = 0;
            for (wq_node=xq_first(wq); wq_node; wq_node=xq_next(wq,wq_node))
            {
                ws = wq_node->data;
                if (ws->work_type == work_type)
                {
                    if (ws->work_prio > info_buf[0])
                        info_buf[0] = ws->work_prio;
                    info_buf[2]++;
                }
            }
            for (wq_node=xq_first(wq); wq_node; wq_node=xq_next(wq,wq_node))
            {
                ws = wq_node->data;
                if (ws->work_type == work_type  &&  ws->work_prio == info_buf[0])
                    info_buf[1]++;
            }
            rc = MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,from_rank,TA_ACK_AND_RC,
                           adlb_all_comm);
        }
        else
        {
            aprintf(1,"** adlb_server: unexpected tag %d recvd from %d on adlb_all_comm\n",
                    from_tag,from_rank);
            exit(-1);
        }
    }
    aprintf(1,"SERVER OUT OF LOOP\n");
    return ADLB_SUCCESS;
}

static int set_reference_and_notify(long id, long value)
{
  DEBUG("set_reference: <%li>=%li", id, value);
  int rc;
  rc = ADLB_Store(id, &value, sizeof(long));
  ADLB_CHECK(rc);
  int* ranks;
  int count;
  rc = ADLB_Close(id, &ranks, &count);
  ADLB_CHECK(rc);
  rc = close_notification(id, ranks, count);
  ADLB_CHECK(rc);
  TRACE("SET_REFERENCE DONE");
  return ADLB_SUCCESS;
}

static int slot_notification(long id)
{
  int rc;
  int* waiters;
  int count;
  rc = data_close(id, &waiters, &count);
  if (count > 0)
  {
    close_notification(id, waiters, count);
    free(waiters);
  }
  return ADLB_SUCCESS;
}

static int close_notification(long id, int* ranks, int count)
{
  int dummy;
  for (int i = 0; i < count; i++)
  {
    char* t;
    int length = asprintf(&t, "close %li", id);
    put_internal(1,        // work_type CONTROL
                 1,        // work_prio
                 -1,       // answer_rank
                 ranks[i], // target_rank
                 length+1, // work_len
                 t,        // work_buf
                 my_world_rank, // ws_hsr home_server_rank
                 0,        // batch_flag OFF
                 0,        // ws_common_len,
                 -1,       // ws_common_server_rank,
                 -1,       // ws_common_server_commseqno
                 &dummy    // exhausted_flag
    );
  }
  return ADLB_SUCCESS;
}

static int put_internal(int work_type, int work_prio,
                        int answer_rank, int target_rank,
                        int work_len, void* work_buf,
                        int ws_hsr, int batch_flag, int ws_common_len,
                        int ws_common_server_rank,
                        int ws_common_server_commseqno,
                        int* exhausted_flag)
{
  /* DEBUG("put_internal: for: %i: %s\n", */
  /*       target_rank, (char*) work_buf); */

  xq_node_t* wq_node =
      wq_node_create(work_type,work_prio,next_wqseqno++,
                     answer_rank,target_rank,work_len,work_buf);
  wq_struct_t* ws = wq_node->data;

     // PTW: if (ws->target_rank >= 0) ws->pinned = 1;
  ws->time_stamp = MPI_Wtime();
  wq_append(wq_node);

  xq_node_t* rq_node =
      /* rank may be -1 */
      rq_find_rank_queued_for_type(target_rank, work_type);
  if (rq_node)
  {
    int info_buf[IBUF_NUMINTS];
    rq_struct_t* rs = rq_node->data;
    ws = wq_node->data;
    ws->pin_rank = rs->world_rank;
    if (ws->pin_rank >= 0)
      ws->pinned = 1;
    info_buf[0] = SUCCESS;
    info_buf[1] = ws->work_type;
    info_buf[2] = ws->work_prio;
    info_buf[3] = ws->work_len;
    info_buf[4] = ws->answer_rank;
    info_buf[5] = ws->wqseqno;
    info_buf[6] = my_world_rank;
    info_buf[7] = ws->common_len;
    info_buf[8] = ws->common_server_rank;
    info_buf[9] = ws->common_server_commseqno;
    aprintf(0000, "IN PUT_HDR, GIVING RESERVATION to %06d\n",rs->world_rank);
    MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,rs->world_rank,
              TA_RESERVE_RESP,adlb_all_comm);
    if (first_time_on_rq[rs->world_rank])
      first_time_on_rq[rs->world_rank] = 0;
    else
    {
      total_time_on_rq += (MPI_Wtime() - rs->time_stamp);
      num_rq_nodes_timed++;
    }

    rq_delete(rq_node);
    *exhausted_flag = 0;
  }
  update_local_state();
  nputmsgs++;

  return ADLB_SUCCESS;
}

static void adlb_server_abort(int code, int mpi_abort_flag)
{
    int info_buf[IBUF_NUMINTS];
    MPI_Request dummy_req;

    aprintf(1,"** adlb_server_abort:  code %d  mpi_abort_flag %d\n",code,mpi_abort_flag);
    print_final_stats();
    info_buf[0] = code;           /* abort code */
    info_buf[1] = my_world_rank;  /* originating rank */
    info_buf[2] = my_world_rank;  /* server for rank (myself) */
    MPI_Isend(info_buf,IBUF_NUMINTS,MPI_INT,rhs_rank,SS_ADLB_ABORT,
              adlb_all_comm,&dummy_req);
    sleep(10);  // give all servers a chance to dump their info
    if (mpi_abort_flag)  /* client should do the abort */
    {
        aprintf(1, "** adlb_server_abort: INVOKING MPI_Abort() \n");
        MPI_Abort(MPI_COMM_WORLD,code);
    }
}

int ADLBP_Debug_server(double timeout)
{
    int rc, msg_available, info_buf[IBUF_NUMINTS],
        num_events = 0, aggr_targeted_wq_size = 0, aggr_untargeted_wq_size = 0,
        aggr_rq_size = 0, aggr_iq_size = 0, aggr_reserve_cnt = 0,
        aggr_reserve_immed_sat = 0, aggr_reserve_not_in_stat_vec = 0, aggr_rfr_failed = 0,
        aggr_ss_msgs_handled = 0, aggr_unexpected_msgqcnt = 0;
    double prev_recv_time, prev_minute_mark;
    MPI_Status status;

    aprintf(1,"I am DEBUG SERVER\n");
    aprintf(1,"** debug_server logging output fields: "   /* multi-line str */
              "num events in last minute; "
              "avg wq size targeted; "
              "avg wq size untargeted; "
              "avg rq size; "
              "avg iq size; "
              "avg reserve cnt; "
              "avg num reserves immediately satisfied; "
              "avg num reserves not in stat vec; "
              "avg num rfr failed; "
              "avg ss msgs handled; "
              "unexpected msg queue size (not available on all hosts); "
              "\n");
    prev_recv_time = MPI_Wtime();
    prev_minute_mark = MPI_Wtime();
    while (1)
    {
        if ((MPI_Wtime() - prev_recv_time) > timeout)
        {
            aprintf(1,"** debug_server: aborting; no msgs recvd lately\n");
            aprintf(1,"** debug_server: num events in last minute %d \n",num_events);
            info_buf[0] = -1;
            /* pretend to be an app since I am not a real server */
            MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,master_server_rank,
                      FA_ADLB_ABORT,adlb_all_comm);
            sleep(10);  /* give servers a chance to dump stats */
            aprintf(1, "** debug_server: invoking MPI_Abort() \n");
            MPI_Abort(MPI_COMM_WORLD,-1);  /* after servers have reacted */
            return -1;  /* should not get here */
        }
        if ((MPI_Wtime() - prev_minute_mark) > 60.0)
        {
            aprintf(1,"** debug_server log data: "   /* multi-line str */
                      "%d  %.1f  %.1f  %.1f  %.1f  %.1f  %.1f  %.1f  %.1f  %.1f  %.1f\n",
                      num_events,
                      (float)aggr_targeted_wq_size/60.0,
                      (float)aggr_untargeted_wq_size/60.0,
                      (float)aggr_rq_size/60.0,
                      (float)aggr_iq_size/60.0,
                      (float)aggr_reserve_cnt/60.0,
                      (float)aggr_reserve_immed_sat/60.0,
                      (float)aggr_reserve_not_in_stat_vec/60.0,
                      (float)aggr_rfr_failed/60.0,
                      (float)aggr_ss_msgs_handled/60.0,
                      (float)aggr_unexpected_msgqcnt/60.0);
            num_events = 0;
            aggr_targeted_wq_size = 0;
            aggr_untargeted_wq_size = 0;
            aggr_rq_size = 0;
            aggr_iq_size = 0;
            aggr_reserve_cnt = 0;
            aggr_reserve_immed_sat = 0;
            aggr_reserve_not_in_stat_vec = 0;
            aggr_rfr_failed = 0;
            aggr_ss_msgs_handled = 0;
            aggr_unexpected_msgqcnt = 0;
            prev_minute_mark = MPI_Wtime();
        }
        rc = MPI_Iprobe(MPI_ANY_SOURCE,MPI_ANY_TAG,adlb_all_comm,&msg_available,&status);
        if ( ! msg_available )
            continue;
        rc = MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,MPI_ANY_SOURCE,MPI_ANY_TAG,
                      adlb_all_comm,&status);
        if (status.MPI_TAG == DS_LOG)
        {
            prev_recv_time = MPI_Wtime();  /* reset time */
            num_events   += info_buf[0];
            aggr_targeted_wq_size += info_buf[1];
            aggr_untargeted_wq_size += info_buf[2];
            aggr_rq_size += info_buf[3];
            aggr_iq_size += info_buf[4];
            aggr_reserve_cnt += info_buf[5];
            aggr_reserve_immed_sat += info_buf[6];
            aggr_reserve_not_in_stat_vec += info_buf[7];
            aggr_rfr_failed += info_buf[8];
            aggr_ss_msgs_handled += info_buf[9];
            aggr_unexpected_msgqcnt += info_buf[10];
        }
        else if (status.MPI_TAG == DS_END)
        {
            aprintf(1,"** debug_server: recvd END\n");
            break;
        }
        else if (status.MPI_TAG == FA_ADLB_ABORT)
        {
            aprintf(1,"** debug_server: received abort %d from rank %d\n",
                    info_buf[0],status.MPI_SOURCE);
            aprintf(1,"** debug_server: num events in last minute %d \n",num_events);
            break;
        }
        else
        {
            aprintf(1,"** debug_server: unexpected msg received  type %d\n",status.MPI_TAG);
        }
    }
    return ADLB_SUCCESS;
}


int ADLBP_Begin_batch_put(void *common_buf, int len_common)
{
    int rc, to_server_rank, put_attempt_cntr, sleep_cntr,
        other_servers_may_have_space, info_buf[IBUF_NUMINTS];
    MPI_Status status;

    // sprintf(log_buf,"BBs\n");
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    if (len_common <= 0)
        return ADLB_SUCCESS;
    other_servers_may_have_space = 1;  /* reset below */
    to_server_rank = next_server_rank_for_put++;
    if (next_server_rank_for_put >= (master_server_rank+num_servers))
        next_server_rank_for_put = master_server_rank;
    sleep_cntr = 0;
    put_attempt_cntr = 0;
    while (1)
    {
        if (put_attempt_cntr  &&  (put_attempt_cntr % num_servers) == 0)
        {
            if (put_attempt_cntr >= (num_servers*2)  &&  ! other_servers_may_have_space)
            {
                aprintf(1,"** batch put: put_attempt_cntr %d\n",put_attempt_cntr);
                sleep(1);
                sleep_cntr++;
                if (sleep_cntr > 1000)
                {
                    aprintf(1,"** rejecting put; put_attempt_cntr %d\n",put_attempt_cntr);
                    return ADLB_PUT_REJECTED;
                }
            }
            other_servers_may_have_space = 0;
            /* may use a nanosleep here sometime; about qmstat_interval */
        }
        put_attempt_cntr++;
        info_buf[0] = len_common;
        rc = MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,to_server_rank,
                       FA_PUT_COMMON_HDR,adlb_all_comm);
        rc = MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,to_server_rank,TA_ACK_AND_RC,
                      adlb_all_comm,&status);
        if (info_buf[0] == ADLB_NO_MORE_WORK)
        {
            aprintf(1,"RETURNING NO_MORE_WORK TO APP\n");
            return info_buf[0];
        }
        else if (info_buf[0] == ADLB_DONE_BY_EXHAUSTION)
        {
            // aprintf(1,"RETURNING DONE_BY_EXHAUSTION TO APP\n");
            return info_buf[0];
        }
        if (info_buf[0] == ADLB_PUT_REJECTED)
        {
            if (info_buf[1] >= 0)  /* rank of another server that may have data */
                other_servers_may_have_space = 1;
            to_server_rank = next_server_rank_for_put++;
            if (next_server_rank_for_put >= (master_server_rank+num_servers))
                next_server_rank_for_put = master_server_rank;
            continue;
        }
        if (info_buf[0] < 0)
            return info_buf[0];  /* e.g. ERROR */
        rc = MPI_Ssend(common_buf,len_common,MPI_BYTE,to_server_rank,
                       FA_PUT_COMMON_MSG,adlb_all_comm);
        rc = MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,to_server_rank,TA_ACK_AND_RC,
                      adlb_all_comm,&status);
        if (info_buf[0] == ADLB_NO_MORE_WORK)
            aprintf(1,"RETURNING NO_MORE_WORK TO APP\n");
        // else if (info_buf[0] == ADLB_DONE_BY_EXHAUSTION)
            // aprintf(1,"RETURNING DONE_BY_EXHAUSTION TO APP\n");
        if (info_buf[0] < 0)  /* e.g. NO_MORE_WORK or ERR */
            return info_buf[0];
        common_len              = len_common;
        common_refcnt           = 0;  /* incremented with each Put */
        common_server_rank      = to_server_rank;
        common_server_commseqno = info_buf[1];
        break;
    }
    // sprintf(log_buf,"BBe\n");
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    return ADLB_SUCCESS;
}

int ADLBP_End_batch_put()
{
    int rc, info_buf[IBUF_NUMINTS];
    MPI_Status status;

    // sprintf(log_buf,"EBs\n");
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    rc = ADLB_SUCCESS;  /* may be reset below */
    if (common_server_rank >= 0)
    {
        info_buf[0] = common_server_commseqno;  /* may be -1 */
        info_buf[1] = common_refcnt;
        MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,common_server_rank,
                  FA_PUT_BATCH_DONE,adlb_all_comm);
        MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,common_server_rank,TA_ACK_AND_RC,
                 adlb_all_comm,&status);
        rc = info_buf[0];
    }
    common_len              =  0;
    common_refcnt           =  0;
    common_server_rank      = -1;
    common_server_commseqno = -1;
    // sprintf(log_buf,"EBe\n");
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);  /* skip rc here */
    if (rc == ADLB_NO_MORE_WORK)
        aprintf(1,"RETURNING NO_MORE_WORK TO APP\n");
    return rc;
}


int ADLBP_Put(void *work_buf, int work_len, int target_rank, int answer_rank,
              int work_type, int work_prio)
{
    int rc, put_attempt_cntr, sleep_cntr, home_server_rank, to_server_rank,
        other_servers_may_have_space, send_buf[IBUF_NUMINTS], info_buf[IBUF_NUMINTS];
    MPI_Status status;
    MPI_Request request;

    DEBUG("ADLB_Put: %s", (char*) work_buf);

    if (work_type < -1  ||  get_type_idx(work_type) < 0)
    {
        aprintf(1,"** invalid work_type %d to ADLB_Put\n",work_type);
        ADLBP_Abort(-1);
    }
    if (target_rank >= 0)
        to_server_rank = num_app_ranks + (target_rank % num_servers);
    else
    {
        to_server_rank = next_server_rank_for_put++;
        if (next_server_rank_for_put >= (master_server_rank+num_servers))
            next_server_rank_for_put = master_server_rank;
    }
    other_servers_may_have_space = 1;  /* reset below */
    home_server_rank = to_server_rank;
    sleep_cntr = 0;
    put_attempt_cntr = 0;
    while (1)
    {
        if (put_attempt_cntr  &&  (put_attempt_cntr % num_servers) == 0)
        {
            if (put_attempt_cntr >= (num_servers*2)  &&  ! other_servers_may_have_space)
            {
                aprintf(1,"** put: put_attempt_cntr %d\n",put_attempt_cntr);
                sleep(1);
                sleep_cntr++;
                if (sleep_cntr > 1000)
                {
                    aprintf(1,"** rejecting put; put_attempt_cntr %d\n",put_attempt_cntr);
                    return ADLB_PUT_REJECTED;
                }
            }
            other_servers_may_have_space = 0;
            /* may use a nanosleep here sometime; about qmstat_interval */
        }
        put_attempt_cntr++;
        send_buf[0] = work_type;
        send_buf[1] = work_prio;
        send_buf[2] = answer_rank;
        send_buf[3] = target_rank;
        send_buf[4] = work_len;
        send_buf[5] = home_server_rank;
        if (inside_batch_put[my_world_rank])
            send_buf[6] = 1;  /* inside batch but not necessarily with common */
        else
            send_buf[6] = 0;
        send_buf[7] = common_len;
        send_buf[8] = common_server_rank;  /* >= 0 -> this is unique part for a common */
        send_buf[9] = common_server_commseqno;  /**/
        rc = MPI_Irecv(info_buf,IBUF_NUMINTS,MPI_INT,to_server_rank,TA_ACK_AND_RC,
                       adlb_all_comm,&request);
        rc = MPI_Send(send_buf,IBUF_NUMINTS,MPI_INT,to_server_rank,FA_PUT_HDR,adlb_all_comm);
        rc = MPI_Wait(&request,&status);
        if (info_buf[0] == ADLB_NO_MORE_WORK)
        {
            aprintf(1,"RETURNING NO_MORE_WORK TO APP\n");
            return info_buf[0];
        }
        else if (info_buf[0] == ADLB_DONE_BY_EXHAUSTION)
        {
            // aprintf(1,"RETURNING DONE_BY_EXHAUSTION TO APP\n");
            return info_buf[0];
        }
        if (info_buf[0] == ADLB_PUT_REJECTED)
        {
            if (info_buf[1] >= 0)  /* rank of another server that may have data */
                other_servers_may_have_space = 1;
            to_server_rank = next_server_rank_for_put++;
            if (next_server_rank_for_put >= (master_server_rank+num_servers))
                next_server_rank_for_put = master_server_rank;
            continue;
        }
        if (info_buf[0] < 0)
            return info_buf[0];  /* e.g. ERROR */
        rc = MPI_Rsend(work_buf,work_len,MPI_BYTE,to_server_rank,FA_PUT_MSG,adlb_all_comm);
        rc = MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,to_server_rank,TA_ACK_AND_RC,
                      adlb_all_comm,&status);
        if (target_rank >= 0  &&  home_server_rank != to_server_rank)
        {
            send_buf[0] = work_type;
            send_buf[1] = target_rank;
            send_buf[2] = to_server_rank;
            rc = MPI_Send(send_buf,IBUF_NUMINTS,MPI_INT,home_server_rank,
                          FA_DID_PUT_AT_REMOTE,adlb_all_comm);
        }
        if (common_len > 0)
            common_refcnt++;
        if (info_buf[0] == ADLB_NO_MORE_WORK)
            aprintf(1,"RETURNING NO_MORE_WORK TO APP\n");
        // else if (info_buf[0] == ADLB_DONE_BY_EXHAUSTION)
            // aprintf(1,"RETURNING DONE_BY_EXHAUSTION TO APP\n");
        if (info_buf[0] < 0)  /* e.g. ERROR  */
            return info_buf[0];
        break;
    }
    // sprintf(log_buf,"Pe tr %d ty%d\n",to_server_rank,work_type);
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    return ADLB_SUCCESS;
}

static inline int
locate(long id)
{
  int offset = ((int) id) % num_servers;
  int rank = num_world_nodes - num_servers + offset;
  // DEBUG("locate(%li) => %i\n", id, rank);
  return rank;
}

/**
   Reusable internal data creation function
   Applications should use the ADLB_Create_type macros in adlb.h
   @param filename Only used for file-type data
   @param subscript_type Only used for container-type data
 */
int ADLBP_Create(adlb_datum_id id, adlb_data_type type,
                 const char* filename, adlb_data_type subscript_type)
{
    int rc, to_server_rank;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    to_server_rank = locate(id);
    struct packed_id_type data = { id, type };

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                   adlb_all_comm, &request);
    MPI_CHECK(rc);

    rc = MPI_Send(&data, sizeof(struct packed_id_type), MPI_BYTE,
                   to_server_rank, FA_CREATE_HDR, adlb_all_comm);
    MPI_CHECK(rc);

    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    ADLB_DATA_CHECK(dc);

    if (type == ADLB_DATA_TYPE_FILE)
    {
      int length = strlen(filename);
      // Remove const qualifier- MPI cannot accept it
      char* fn = (char*) filename;
      rc = MPI_Send(fn, length+1, MPI_CHAR, to_server_rank,
                     FA_CREATE_MSG, adlb_all_comm);
      MPI_CHECK(rc);
    }
    else if (type == ADLB_DATA_TYPE_CONTAINER)
    {
      TRACE("ADLB_Create(type=container, subscript_type=%i)",
            subscript_type);
      rc = MPI_Send(&subscript_type, 1, MPI_INT, to_server_rank,
                     FA_CREATE_MSG, adlb_all_comm);
      MPI_CHECK(rc);
    }
    return ADLB_SUCCESS;
}

int ADLB_Create_integer(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_INTEGER, NULL, ADLB_DATA_TYPE_NULL);
}

int ADLB_Create_float(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_FLOAT, NULL, ADLB_DATA_TYPE_NULL);
}

int ADLB_Create_string(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_STRING, NULL, ADLB_DATA_TYPE_NULL);
}

int ADLB_Create_blob(adlb_datum_id id)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_BLOB, NULL, ADLB_DATA_TYPE_NULL);
}

int ADLB_Create_file(adlb_datum_id id, const char* filename)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_FILE, filename, ADLB_DATA_TYPE_NULL);
}

int ADLB_Create_container(adlb_datum_id id, adlb_data_type subscript_type)
{
  return ADLB_Create(id, ADLB_DATA_TYPE_CONTAINER, NULL, subscript_type);
}

int ADLBP_Exists(adlb_datum_id id, bool* result)
{
  int to_server_rank = locate(id);

  int rc;
  MPI_Status status;
  MPI_Request request;

  TRACE("ADLB_Exists: <%li>\n", id);

  rc = MPI_Irecv(result, sizeof(bool), MPI_BYTE, to_server_rank,
                 TA_ACK_AND_RC, adlb_all_comm, &request);

  MPI_CHECK(rc);

  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank, FA_EXISTS_HDR,
                adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);

  return ADLB_SUCCESS;
}

int ADLBP_Store(adlb_datum_id id, void *data, int length)
{
  int to_server_rank;
  int rc;
  adlb_data_code dc;

  MPI_Status status;
  MPI_Request request;

  to_server_rank = locate(id);

  if (to_server_rank == my_world_rank)
  {
    // This is a server-to-server operation on myself
    TRACE("Store SELF");
    dc = data_store(id, data, length);
    ADLB_DATA_CHECK(dc);
    return ADLB_SUCCESS;
  }

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                  adlb_all_comm,&request);
  MPI_CHECK(rc);

  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank, FA_STORE_HDR,
                 adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Send(data, length, MPI_BYTE, to_server_rank,
		FA_STORE_MSG, adlb_all_comm);
  MPI_CHECK(rc);

  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);
  return ADLB_SUCCESS;
}

/**
   Obtain a random server index
   Currently implemented as a round-robin loop through the ranks
 */
static inline int
random_server()
{
  static int random_server_index = 0;
  int offset = random_server_index % num_servers;
  int rank = num_world_nodes - num_servers + offset;
  // DEBUG("random_server => %i\n", rank);
  random_server_index = (random_server_index + 1) % num_servers;
  return rank;
}

int ADLBP_Slot_create(long id)
{
    adlb_data_code dc;
    int rc;

    MPI_Status status;
    MPI_Request request;

    DEBUG("ADLB_Slot_create: <%li>", id);
    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                    adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                   FA_SLOT_CREATE_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    if (dc != ADLB_DATA_SUCCESS)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

int ADLBP_Slot_drop(long id)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    DEBUG("ADLB_Slot_drop: <%li>", id);
    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                    adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                   FA_SLOT_DROP_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    if (dc != ADLB_DATA_SUCCESS)
        return ADLB_ERROR;
    return ADLB_SUCCESS;
}

int ADLBP_Insert(adlb_datum_id id,
                 const char* subscript, const char* member,
                 int member_length, int drops)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    CHECK_MSG(member_length < ADLB_DATA_MEMBER_MAX,
              "ADLB_Insert(): member too long: <%li>[\"%s\"]\n",
              id, subscript);

    DEBUG("ADLB_Insert: <%li>[\"%s\"]=\"%s\"", id, subscript, member);
    int length = sprintf(xfer, "%li %s %i %i",
                         id, subscript, member_length, drops);
    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                    adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(xfer, length+1, MPI_INT, to_server_rank,
                   FA_INSERT_HDR, adlb_all_comm);
    MPI_CHECK(rc);

    rc = MPI_Send((char*) member, member_length+1, MPI_BYTE,
                  to_server_rank, FA_INSERT_MSG, adlb_all_comm);
    MPI_CHECK(rc);

    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    if (dc != ADLB_DATA_SUCCESS)
        return ADLB_ERROR;

    return ADLB_SUCCESS;
}

int ADLBP_Insert_atomic(adlb_datum_id id, const char* subscript,
                        bool* result)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request1, request2;

    DEBUG("ADLB_Insert_atomic: <%li>[\"%s\"]", id, subscript);
    int length = sprintf(xfer, "%li %s", id, subscript);
    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                    adlb_all_comm, &request1);
    MPI_CHECK(rc);
    rc = MPI_Irecv(result, sizeof(bool), MPI_BYTE, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm, &request2);
    MPI_CHECK(rc);
    rc = MPI_Send(xfer, length+1, MPI_INT, to_server_rank,
                  FA_INSERT_ATOMIC_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request1, &status);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request2, &status);
    MPI_CHECK(rc);

    if (dc != ADLB_DATA_SUCCESS)
        return ADLB_ERROR;
    return ADLB_SUCCESS;
}

int ADLBP_Retrieve(adlb_datum_id id, adlb_data_type* type,
		   void *data, int *length)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm, &request);

    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  FA_RETRIEVE_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);

    if (dc == ADLB_DATA_SUCCESS)
    {
      rc = MPI_Recv(type, 1, MPI_INT, to_server_rank,
		    TA_ACK_AND_RC, adlb_all_comm, &status);
      MPI_CHECK(rc);
      rc = MPI_Recv(data, ADLB_MSG_MAX, MPI_BYTE, to_server_rank,
		    TA_ACK_AND_RC, adlb_all_comm, &status);
      MPI_CHECK(rc);
    }
    else
      return ADLB_ERROR;

    // Set length output parameter
    MPI_Get_count(&status, MPI_BYTE, length);
    // DEBUG("RETRIEVE: <%li>=%s\n", hashcode, (char*) data);
    return ADLB_SUCCESS;
}

/**
   Allocates fresh memory in subscripts and members
   Caller must free this when done
 */
int ADLBP_Enumerate(adlb_datum_id container_id,
                    int count, int offset,
                    char** subscripts, int* subscripts_length,
                    char** members, int* members_length,
                    int* restrict records)
{
  int rc;
  adlb_data_code dc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(container_id);

  struct packed_enumerate opts;
  opts.id = container_id;
  // Are we requesting subscripts?
  opts.request_subscripts = *subscripts ? 1 : 0;
  // Are we requesting members?
  opts.request_members = *members ? 1 : 0;
  opts.count = count;
  opts.offset = offset;

  rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank,
                 TA_ACK_AND_RC, adlb_all_comm, &request);
  MPI_CHECK(rc);

  rc = MPI_Send(&opts, sizeof(struct packed_enumerate), MPI_BYTE,
                to_server_rank, FA_ENUMERATE_HDR, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request,&status);
  MPI_CHECK(rc);

  if (dc == ADLB_DATA_SUCCESS)
  {
    rc = MPI_Recv(records, 1, MPI_INT, to_server_rank,
                  TA_ACK_AND_RC, adlb_all_comm, &status);
    MPI_CHECK(rc);

    if (*subscripts)
    {
      rc = MPI_Recv(xfer, ADLB_MSG_MAX, MPI_BYTE, to_server_rank,
                    TA_ACK_AND_RC, adlb_all_comm, &status);
      MPI_CHECK(rc);
      *subscripts = strdup(xfer);
      // Set length output parameter
      MPI_Get_count(&status, MPI_BYTE, subscripts_length);
    }
    if (*members)
    {
      rc = MPI_Recv(xfer, ADLB_MSG_MAX, MPI_BYTE, to_server_rank,
                    TA_ACK_AND_RC, adlb_all_comm, &status);
      MPI_CHECK(rc);
      int c;
      MPI_Get_count(&status, MPI_BYTE, &c);
      char* A = malloc(c);
      assert(A);
      memcpy(A, xfer, c);
      *members = A;
      *members_length = c;
    }
    MPI_CHECK(rc);
  }
  else
    return ADLB_ERROR;

  return ADLB_SUCCESS;
}

int ADLBP_Unique(long* result)
{
    int rc;
    MPI_Status status;
    MPI_Request request;

    // This is just something to send, it is ignored by the server
    static int msg = 0;
    int to_server_rank = random_server();
    rc = MPI_Irecv(result, 1, MPI_LONG, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&msg, 1, MPI_INT, to_server_rank,
                  FA_UNIQUE_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);

    if (result == ADLB_DATA_ID_NULL)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

int ADLBP_Typeof(adlb_datum_id id, adlb_data_type* type)
{
    int rc;
    MPI_Status status;
    MPI_Request request;

    int to_server_rank = locate(id);
    rc = MPI_Irecv(type, 1, MPI_INT, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  FA_TYPEOF_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);

    DEBUG("ADLB_Typeof <%li>=>%i", id, *type);

    if (*type == -1)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

int ADLBP_Container_typeof(adlb_datum_id id, adlb_data_type* type)
{
    int rc;
    MPI_Status status;
    MPI_Request request;

    // DEBUG("ADLB_Container_typeof: %li", id);

    int to_server_rank = locate(id);
    rc = MPI_Irecv(type, 1, MPI_INT, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  FA_CONTAINER_TYPEOF_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);

    DEBUG("ADLB_Container_typeof <%li>=>%i", id, *type);

    if (*type == -1)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

/**
   Look in given container for subscript
   Store result in output member
   On error, output member is ADLB_DATA_ID_NULL
   @param member Must be pre-allocated to ADLB_DATA_MEMBER_MAX
 */
int ADLBP_Lookup(adlb_datum_id id,
                 const char* subscript, char* member, int* found)
{
    int rc, to_server_rank;
    MPI_Status status;
    MPI_Request request;

    // DEBUG("lookup: %li\n", hashcode);

    to_server_rank = locate(id);

    char msg[ADLB_DATA_SUBSCRIPT_MAX+32];
    sprintf(msg, "%li %s", id, subscript);
    int msg_length = strlen(msg)+1;

    struct packed_code_length p;
    rc = MPI_Irecv(&p, sizeof(p), MPI_BYTE, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm,&request);
    MPI_CHECK(rc);
    rc = MPI_Send(msg, msg_length, MPI_CHAR, to_server_rank,
                  FA_LOOKUP_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);

    if (p.code != ADLB_DATA_SUCCESS)
      return ADLB_ERROR;

    if (p.length == 1)
    {
      rc = MPI_Recv(member, ADLB_DATA_MEMBER_MAX, MPI_BYTE, to_server_rank,
                    TA_ACK_AND_RC, adlb_all_comm, &status);
      MPI_CHECK(rc);
      *found = 1;
    }
    else
      *found = -1;

    return ADLB_SUCCESS;
}

/**
   @param subscribed output: false if data is already closed
                             or ADLB_ERROR on error
 */
int ADLBP_Subscribe(long id, int* subscribed)
{
    int rc, to_server_rank;
    MPI_Status status;
    MPI_Request request;

    to_server_rank = locate(id);

    rc = MPI_Irecv(subscribed, 1, MPI_INT, to_server_rank,
                   TA_ACK_AND_RC, adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                  FA_SUBSCRIBE_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request,&status);
    MPI_CHECK(rc);
    DEBUG("ADLB_Subscribe: <%li> => %i", id, *subscribed);

    if (*subscribed == -1)
      return ADLB_ERROR;

    return ADLB_SUCCESS;
}

/**
   @return false in subscribed if data is already closed
 */
int
ADLBP_Container_reference(adlb_datum_id id, const char *subscript,
                          adlb_datum_id reference)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    int length = sprintf(xfer, "%li %li %s",
                         reference, id, subscript);

    int to_server_rank = locate(id);

    rc = MPI_Irecv(&dc, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                   adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(xfer, length+1, MPI_CHAR, to_server_rank,
                  FA_CONTAINER_REFERENCE_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    DEBUG("ADLB_Container_reference: <%li>[\"%s\"] => <%li>",
          id, subscript, reference);

    if (dc != ADLB_DATA_SUCCESS)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

/** Return ADLB_ERROR and size=-1 if container is not closed */
int ADLBP_Container_size(adlb_datum_id container_id, int* size)
{
    int rc;
    adlb_data_code dc;
    MPI_Status status;
    MPI_Request request;

    int to_server_rank = locate(container_id);

    rc = MPI_Irecv(size, 1, MPI_INT, to_server_rank, TA_ACK_AND_RC,
                   adlb_all_comm, &request);
    MPI_CHECK(rc);
    rc = MPI_Send(&container_id, 1, MPI_LONG, to_server_rank,
                  FA_CONTAINER_SIZE_HDR, adlb_all_comm);
    MPI_CHECK(rc);
    rc = MPI_Wait(&request, &status);
    MPI_CHECK(rc);
    DEBUG("ADLB_Container_size: <%li> => %i",
          container_id, *size);

    if (*size < 0)
      return ADLB_ERROR;
    return ADLB_SUCCESS;
}

int ADLBP_Close(adlb_datum_id id, int** ranks, int *count)
{
  int rc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(id);

  if (to_server_rank == my_world_rank)
  {
    TRACE("CLOSE SELF: <%li>\n", id);
    adlb_data_code dc = data_close(id, ranks, count);
    ADLB_DATA_CHECK(dc);
    return ADLB_SUCCESS;
  }

  rc = MPI_Irecv(count, 1, MPI_INT, to_server_rank,
                 TA_ACK_AND_RC, adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                FA_CLOSE_HDR, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request,&status);
  MPI_CHECK(rc);

  if (*count == -1)
    return ADLB_ERROR;

  if (*count > 0)
  {
    *ranks = malloc(*count*sizeof(int));
    rc = MPI_Recv(*ranks, *count, MPI_INT, to_server_rank,
                  TA_ACK_AND_RC, adlb_all_comm, &status);
    MPI_CHECK(rc);
  }

  return ADLB_SUCCESS;
}

/**
   @return result 0->try again, 1->locked
 */
int ADLBP_Lock(adlb_datum_id id, bool* result)
{
  int rc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(id);

  // c 0->try again, 1->locked, x->failed
  char c;
  rc = MPI_Irecv(&c, 1, MPI_CHAR, to_server_rank,
                 TA_ACK_AND_RC, adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                FA_LOCK_HDR, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request,&status);
  MPI_CHECK(rc);

  if (c == 'x')
    return ADLB_ERROR;

  if (c == '0')
    *result = false;
  else if (c == '1')
    *result = true;
  else
    assert(false);

  return ADLB_SUCCESS;
}

/**
   @return result 0->try again, 1->locked
 */
int ADLBP_Unlock(adlb_datum_id id)
{
  int rc;
  MPI_Status status;
  MPI_Request request;

  int to_server_rank = locate(id);

  // c: 1->success, x->failed
  char c;
  rc = MPI_Irecv(&c, 1, MPI_CHAR, to_server_rank,
                 TA_ACK_AND_RC, adlb_all_comm, &request);
  MPI_CHECK(rc);
  rc = MPI_Send(&id, 1, MPI_LONG, to_server_rank,
                FA_UNLOCK_HDR, adlb_all_comm);
  MPI_CHECK(rc);
  rc = MPI_Wait(&request, &status);
  MPI_CHECK(rc);

  if (c == 'x')
    return ADLB_ERROR;

  if (c != '1')
    assert(false);

  return ADLB_SUCCESS;
}

int ADLBP_Reserve(int *req_types, int *work_type, int *work_prio, int *work_handle,
                  int *work_len, int *answer_rank)
{
    int rc;

    rc = adlbp_Reserve(req_types,work_type,work_prio,work_handle,work_len,answer_rank,1);
    return rc;
}

int ADLBP_Ireserve(int *req_types, int *work_type, int *work_prio, int *work_handle,
                   int *work_len, int *answer_rank)
{
    int rc;

    rc = adlbp_Reserve(req_types,work_type,work_prio,work_handle,work_len,answer_rank,0);
    return rc;
}

int adlbp_Reserve(int *req_types, int *work_type, int *work_prio, int *work_handle,
                  int *work_len, int *answer_rank, int hang_flag)
{
    int i, j, rc, reserve_buf[REQ_TYPE_VECT_SZ+1], info_buf[IBUF_NUMINTS];
    MPI_Status status;
    MPI_Request request;

    // printf("%s\n", __FUNCTION__);

    for (i=0; i < REQ_TYPE_VECT_SZ; i++)
    {
        if (req_types[i] == -1)
            break;    /* don't validate the rest */
        if (req_types[i] < -1  ||  (req_types[i] > -1 && (get_type_idx(req_types[i]) < 0)))
        {
            aprintf(1,"** invalid req_type %d to adlb reserve\n",req_types[i]);
            ADLBP_Abort(-1);
        }
    }
    reserve_buf[0] = hang_flag;
    reserve_buf[1] = req_types[0];
    for (i=1; i < REQ_TYPE_VECT_SZ; i++)  /* start at 1 */
    {
        /* if first or any subsequent is -1, set all rest to -2 (invalid) */
        if (req_types[0] == -1  ||  req_types[i] == -1)
        {
            for (j=i; j < REQ_TYPE_VECT_SZ; j++)
                reserve_buf[j+1] = -2;
            break;
        }
        else
            reserve_buf[i+1] = req_types[i];
    }
    // sprintf(log_buf,"Rs tys %d %d %d %d 3inlist %d\n",
            // req_types[0],req_types[1],req_types[2],req_types[3],j);
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    rc = MPI_Irecv(info_buf,IBUF_NUMINTS,MPI_INT,my_server_rank,
                   TA_RESERVE_RESP,adlb_all_comm,&request);
    rc = MPI_Send(reserve_buf,REQ_TYPE_VECT_SZ+1,MPI_INT,my_server_rank,
                  FA_RESERVE,adlb_all_comm);
    rc = MPI_Wait(&request,&status);
    if (info_buf[0] == NO_CURR_WORK)    /* NO_CURR_WORK */
    {
        rc = ADLB_NO_CURRENT_WORK;
    }
    else if (info_buf[0] < 0)
    {
        rc = info_buf[0];   /* NO_MORE_WORK or EXHAUSTION */
    }
    else
    {
        *work_type      = info_buf[1];
        *work_prio      = info_buf[2];
        *work_len       = info_buf[3];
        *answer_rank    = info_buf[4];
        work_handle[0]  = info_buf[5];  /* seqno of wq work packet */
        work_handle[1]  = info_buf[6];  /* server rank where data is located */
        work_handle[2]  = info_buf[7];  /* common_len */
        if (info_buf[7] > 0)
            *work_len += info_buf[7];
        work_handle[3]  = info_buf[8];  /* common_server_rank */
        work_handle[4]  = info_buf[9];  /* common_server_commseqno */
        aprintf(0000,"WORKHANDLE totlen %d wkseq %d srvrank %d commlen %d commsrvr %d commseq %d\n",
                *work_len,work_handle[0],work_handle[1],work_handle[2],work_handle[3],work_handle[4]);
        rc = ADLB_SUCCESS;
    }
    if (rc == ADLB_NO_MORE_WORK)
        aprintf(1,"RETURNING NO_MORE_WORK TO APP\n");
    // else if (info_buf[0] == ADLB_DONE_BY_EXHAUSTION)
        // aprintf(1,"RETURNING DONE_BY_EXHAUSTION TO APP\n");
    // sprintf(log_buf,"Re\n");
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);  /* skip rc here */
    return rc;
}


int ADLBP_Get_reserved(void *work_buf, int *work_handle)
{
    int rc;

    // printf("%s\n", __FUNCTION__);

    rc = adlbp_Get_reserved_timed(work_buf, work_handle, NULL);
    return rc;
}

int ADLBP_Get_reserved_timed(void *work_buf, int *work_handle, double *queued_time)
{
    int rc;

    rc = adlbp_Get_reserved_timed(work_buf, work_handle, queued_time);
    return rc;
}

int adlbp_Get_reserved_timed(void *work_buf, int *work_handle, double *queued_time)
{
    int rc, from_server_rank, info_buf[IBUF_NUMINTS], commlen, work_len;
    double dbls_info_buf[IBUF_NUMDBLS];
    MPI_Status status;
    MPI_Request request;

    /* first get the common if it exists */
    commlen = work_handle[2];
    from_server_rank = work_handle[3];
    info_buf[0] = work_handle[4];  /* cqseqno */

    if (commlen)
    {
        rc = MPI_Send(info_buf,IBUF_NUMINTS,MPI_INT,from_server_rank,
                      FA_GET_COMMON,adlb_all_comm);
        rc = MPI_Recv(work_buf,commlen,MPI_BYTE,from_server_rank,
                      TA_GET_COMMON_RESP,adlb_all_comm,&status);
    }

    /* then get the unique data part */
    info_buf[0] = work_handle[0];
    from_server_rank = work_handle[1];
    // sprintf(log_buf,"Gs r%d\n",from_server_rank);
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    rc = MPI_Irecv(dbls_info_buf,IBUF_NUMDBLS,MPI_DOUBLE,from_server_rank,
                   TA_ACK_AND_RC,adlb_all_comm,&request);
    rc = MPI_Send(info_buf,IBUF_NUMINTS,MPI_INT,from_server_rank,
                  FA_GET_RESERVED,adlb_all_comm);
    rc = MPI_Wait(&request,&status);
    if ((int)dbls_info_buf[0] < 0)
    {
      puts("adlb: NO_MORE_WORK or EXHAUSTION");
        rc = (int)dbls_info_buf[0];  /* NO_MORE_WORK or EXHAUSTION */
    }
    else
    {
        work_len = (int)dbls_info_buf[1];
        rc = MPI_Recv((void *)(((char *)work_buf) + commlen),work_len,MPI_BYTE,
                      from_server_rank,TA_GET_RESERVED_RESP,adlb_all_comm,&status);
        // printf("work_buf: %i %s\n", work_len, (char*) work_buf);
        if (queued_time)
            *queued_time = dbls_info_buf[2];
        rc = 1;
    }
    if (rc == ADLB_NO_MORE_WORK)
        printf("RETURNING NO_MORE_WORK TO APP\n");
    // else if (info_buf[0] == ADLB_DONE_BY_EXHAUSTION)
        // aprintf(1,"RETURNING DONE_BY_EXHAUSTION TO APP\n");
    // sprintf(log_buf,"Ge r%d\n",from_server_rank);
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);  /* skip rc here */
    return rc;
}

int ADLBP_Info_num_work_units(int work_type, int *max_prio, int *num_max_prio_type, int *num_type)
{
    int rc, info_buf[IBUF_NUMINTS];
    MPI_Status status;

    if (get_type_idx(work_type) < 0)
    {
        aprintf(1,"** aborting: INVALID TYPE %d\n",work_type);
        ADLBP_Abort(-1);
    }
    info_buf[0] = work_type;
    MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_BYTE,my_server_rank,FA_INFO_NUM_WORK_UNITS,adlb_all_comm);
    rc = MPI_Recv(info_buf,IBUF_NUMINTS,MPI_INT,my_server_rank,TA_ACK_AND_RC,
                  adlb_all_comm,&status);
    *max_prio = info_buf[0];
    *num_max_prio_type = info_buf[1];
    *num_type = info_buf[2];
    rc = info_buf[3];  /* may be no more work */
    return rc;
}

int ADLBP_Set_no_more_work()  // deprecated to Set_problem_done
{
    ADLBP_Set_problem_done();
    return ADLB_SUCCESS;
}

int ADLBP_Set_problem_done()
{
    // sprintf(log_buf,"SNMW\n");
    // MPI_Ssend(log_buf,100,MPI_BYTE,my_server_rank,FA_LOG,adlb_all_comm);
    MPI_Ssend(NULL,0,MPI_BYTE,my_server_rank,FA_NO_MORE_WORK,adlb_all_comm);
    return ADLB_SUCCESS;
}

int adlbp_Probe(int dest, int tag, MPI_Comm  comm, MPI_Status *status)
{
    int rc;

    rc = MPI_Probe(dest,tag,comm,status);
    return rc;
}

int ADLBP_Info_get(int key, double *val)
{
    if (key == ADLB_INFO_MALLOC_HWM)
    {
        *val = hwm_bytes_dmalloced;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_AVG_TIME_ON_RQ)
    {
        if (num_rq_nodes_timed > 0)
            *val = (double) (total_time_on_rq / num_rq_nodes_timed);
        else
            *val = (double) 0.0;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_NPUSHED_FROM_HERE)
    {
        *val = (double) npushed_from_here;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_NPUSHED_TO_HERE)
    {
        *val = (double) npushed_to_here;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_NREJECTED_PUTS)
    {
        *val = (double) num_rejected_puts;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_LOOP_TOP_TIME)
    {
        *val = total_looptop_time;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_NUM_RESERVES)
    {
        *val = num_reserves;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_NUM_RESERVES_PUT_ON_RQ)
    {
        *val = num_reserves_put_on_rq;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_MAX_QMSTAT_TRIP_TIME)
    {
        *val = max_qmstat_trip_time;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_AVG_QMSTAT_TRIP_TIME)
    {
        if (nqmstatmsgs <= 0)
            *val = 0.0;
        else
            *val = sum_of_qmstat_trip_times / (double)nqmstatmsgs;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_NUM_QMS_EXCEED_INT)
    {
        *val = (double)num_qmstats_exceeded_interval;
        return ADLB_SUCCESS;
    }
    else if (key == ADLB_INFO_MAX_WQ_COUNT)
    {
        *val = (double)wq->max_count;
        return ADLB_SUCCESS;
    }
    return ADLB_ERROR;
}

int
ADLBP_Finalize()
{
  int rc;
  int flag;
  MPI_Finalized(&flag);
  if (flag)
  {
    printf("ERROR: MPI_Finalize() called before ADLB_Finalize()\n");
    return ADLB_ERROR;
  }
  data_finalize();
  if (my_world_rank >= master_server_rank)
  {
    if (my_world_rank != debug_server_rank)
      print_final_stats();
  }
  else  /* app; not a server */
  {
    rc = MPI_Ssend(NULL,0,MPI_INT,my_server_rank,FA_LOCAL_APP_DONE,adlb_all_comm);
  }
  return ADLB_SUCCESS;
}

int ADLBP_Abort(int code)
{
    int info_buf[IBUF_NUMINTS];
    printf("ADLBP_Abort()\n");
    info_buf[0] = code;
    MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,my_server_rank,FA_ADLB_ABORT,adlb_all_comm);
    MPI_Ssend(info_buf,IBUF_NUMINTS,MPI_INT,debug_server_rank,FA_ADLB_ABORT,adlb_all_comm);
    sleep(1);  /* give servers a chance to dump stats */
    // aprintf(1, "** app: invoking MPI_Abort() \n");
    MPI_Abort(MPI_COMM_WORLD,code);  /* only after servers have all reacted */
    return -1;  /* should not get here due to Abort above */
}

static void pack_qmstat()  /* pack ALL qmstat_tbl info into qmstat_send_buf */
{
    int i, pos, len;

    pos = 0;
    for (i=0; i < num_servers; i++)
    {
        /* put in the hi prio for each type */
        len = num_types * sizeof(int);
        memcpy(((char*)qmstat_send_buf)+pos,&qmstat_tbl[i].type_hi_prio[0],len);
        pos += len;
        /* put in the qlen of unpinned and untargeted */
        len = sizeof(int);
        memcpy(((char*)qmstat_send_buf)+pos,&qmstat_tbl[i].qlen_unpin_untarg,len);
        pos += len;
        /* put in the number of bytes currently being used by amallocs */
        len = sizeof(double);
        memcpy(((char*)qmstat_send_buf)+pos,&qmstat_tbl[i].nbytes_used,len);
        pos += len;
    }
}

static void unpack_qmstat()
{
    int i, pos, len;

    pos = 0;
    for (i=0; i < num_servers; i++)
    {
        /* put in the hi prio for each type */
        len = num_types * sizeof(int);
        memcpy(&qmstat_tbl[i].type_hi_prio[0],((char*)qmstat_recv_buf)+pos,len);
        pos += len;
        /* put in the qlen of unpinned and untargeted */
        len = sizeof(int);
        memcpy(&qmstat_tbl[i].qlen_unpin_untarg,((char*)qmstat_recv_buf)+pos,len);
        pos += len;
        /* put in the number of bytes currently being used by amallocs */
        len = sizeof(double);
        memcpy(&qmstat_tbl[i].nbytes_used,((char*)qmstat_recv_buf)+pos,len);
        pos += len;
    }
}

static void log_at_debug_server()
{
    int rc, info_buf[IBUF_NUMINTS];
    xq_node_t *wq_node, *iq_node;
    wq_struct_t *ws;
    MPI_Request *temp_req;

    info_buf[0] = num_events_since_logatds;
    info_buf[1] = 0;  /* initially */
    for (wq_node=xq_first(wq); wq_node; wq_node=xq_next(wq,wq_node))
    {
        ws = wq_node->data;
        if (ws->target_rank >= 0)
            info_buf[1]++;    /* count of targeted work */
    }
    info_buf[2] = wq->count - info_buf[1];    /* count of UN-targeted work */
    info_buf[3] = rq->count;
    info_buf[4] = iq->count;
    info_buf[5] = num_reserves_since_logatds;
    info_buf[6] = num_reserves_immed_sat_since_logatds;
    info_buf[7] = num_reserves_not_in_stat_vec;
    info_buf[8] = num_rfr_failed_since_logatds;
    info_buf[9] = num_ss_msgs_handled_since_logatds;
#   ifdef DEBUGGING_BGP
    info_buf[10] = GetUnexpectedRequestCount();  /* BGP-specific */
#   else
#   ifdef DEBUGGING_SICORTEX
    info_buf[10] = MPIDI_Debug_early_queue_length();
#   else
    info_buf[10] = 0;
#   endif
#   endif
    temp_req = amalloc(sizeof(MPI_Request));
    rc = MPI_Issend(info_buf,IBUF_NUMINTS,MPI_INT,debug_server_rank,DS_LOG,
                    adlb_all_comm,temp_req);
    iq_node = iq_node_create(temp_req,0,NULL);
    iq_append(iq_node);
}

static void print_final_stats()
{
    int i;

    aprintf(1,"runtime %f secs\n",MPI_Wtime()-job_start_time);
    print_proc_self_status();
    print_curr_mem_and_queue_status();
    if (use_circ_buffs)
    {
        print_circular_buffers();
        /* already printing the following in print_curr_mem_and_queue_status above */
        /****
        wq_print_info();
        rq_print_info(num_types);
        tq_print_info();
        iq_print_info();
        ****/
    }
    if (num_rq_nodes_timed > 0)
        aprintf(1,"Average Time on RQ: %f ;  malloc hwm: %.0f\n",
                (total_time_on_rq / num_rq_nodes_timed),hwm_bytes_dmalloced);
    else
        aprintf(1,"Average Time on RQ: 0 ;  malloc hwm: %.0f\n",hwm_bytes_dmalloced);
    aprintf(1,"  nputmsgs %d  \n",nputmsgs);
    aprintf(1,"  npushed_from_here %d  npushed_to_here %d\n",
            npushed_from_here,npushed_to_here);
    aprintf(1,"  nrfrs_sent %d  nrfrs_recvd %d \n",nrfrs_sent,nrfrs_recvd);
    aprintf(1,"  max wq count %d  \n",wq->max_count);
    aprintf(1,"  num_tq_nodes fixed %d  \n",num_tq_nodes_fixed);
    if (my_world_rank == master_server_rank)
    {
        aprintf(1,"  nqmstatmsgs %d\n",nqmstatmsgs);
        if (nqmstatmsgs)
            aprintf(1,"  avg qmstat trip time  %f\n",
                    sum_of_qmstat_trip_times/(double)nqmstatmsgs);
        else
            aprintf(1,"  avg qmstat trip time  0.0\n");
        aprintf(1,"  max qmstat trip time  %f\n",max_qmstat_trip_time);
    }
    aprintf(1,"  total looptop time %f\n",total_looptop_time);
    aprintf(1,"  num_reserves %.0f  num_reserves_put_on_rq %.0f\n",
            num_reserves,num_reserves_put_on_rq);
    /* nbytes unfreed may be slightly > 0 for ranks > 0 because of ss_end_loop_2 msg */
    aprintf(1,"rough num bytes unfreed %.0f\n",curr_bytes_dmalloced-init_fixed_dmalloced);
    for (i=0; i < num_app_ranks; i++)
        if (inside_batch_put[i])
            aprintf(1,"rank %06d was still inside a batch put\n",i);
}

static void print_circular_buffers()
{
    int i, j;

    aprintf(1,"** dumping circ buffers:\n");
    for (i=0; i < num_world_nodes; i++)
    {
        j = bufidx[i];
        do
        {
            if (cbuffers[i][j][0])
                fprintf(stderr,"cbrank %06d: %s",i,cbuffers[i][j]);
            j = (j + 1) % num_bufs_in_circle;
        }  while (j != bufidx[i]);  /* loop back to where I started */
    }
}

static void print_curr_mem_and_queue_status()
{
    aprintf(1,"current mem and queue status info:\n");
    aprintf(1,"    current num bytes %.0f  MB %.0f\n",
            curr_bytes_dmalloced,curr_bytes_dmalloced/1000000.0);
    aprintf(1,"    total bytes malloced over time %.0f  MB %.0f\n",
            total_bytes_dmalloced,total_bytes_dmalloced/1000000.0);
    aprintf(1,"    malloc hwm: %.0f\n", hwm_bytes_dmalloced);
    if (wq)
        wq_print_info();
    if (cq)
        cq_print_info();
    if (rq)
        rq_print_info(num_types);
    if (tq)
        tq_print_info();
    if (iq)
        iq_print_info();
}

static void print_proc_self_status()
{
    int val;
    char input_line[1024], key[100], mag[100];
    FILE *statsfile;

    statsfile = fopen("/proc/self/status","r");
    if (statsfile)
    {
        aprintf(1,"values from: /proc/self/status:\n");
        while (fgets(input_line,100,statsfile) != NULL)
        {
            if (strncmp(input_line,"VmRSS:",6)  == 0  ||
                strncmp(input_line,"VmHWM:",6)  == 0  ||
                strncmp(input_line,"VmPeak:",7) == 0  ||
                strncmp(input_line,"VmSize:",7) == 0)
            {
                sscanf(input_line,"%s %d %s",key,&val,mag);
                aprintf(1,"    %s %d %s\n",key,val,mag);
            }
        }
    }
}

/*
static void cblog(int flag, int for_rank, char *fmt, ...)  /* circ buff log
{
    char *s;
    va_list ap;

    if ( ! use_circ_buffs)
        return;
    if ( ! flag)
        return;
    va_start( ap, fmt );
    int n = vasprintf(&s, fmt, ap);
    assert(n > 0);
    va_end(ap);
    if ( ! s )
    {
        fprintf(stderr,"**** FAILED TO ALLOCATE MEMORY FOR LOG MSG ****\n");
        fflush(stderr);
        return;
    }
    sprintf(cbuffers[for_rank][bufidx[for_rank]],"time %f : ",MPI_Wtime()-job_start_time);
    strcat(cbuffers[for_rank][bufidx[for_rank]],s);
    bufidx[for_rank] = (bufidx[for_rank] + 1) % num_bufs_in_circle;
    free(s);
}
*/

void adlbp_dbgprintf(int flag, int linenum, char *fmt, ...)
{
    char *s;
    va_list ap;

    if ( ! dbgprintf_flag)
        return;
    if ( ! flag)
        return;
    va_start( ap, fmt );
    int n = vasprintf(&s, fmt, ap);
    assert(n > 1);
    va_end(ap);
    if ( ! s )
    {
        fprintf(stderr,"**** FAILED TO ALLOCATE MEMORY FOR DEBUG PRINT ****\n");
        fflush(stderr);
        return;
    }
    fprintf(stderr,"%06d: %4d: %f:  %s",my_world_rank,linenum,MPI_Wtime()-job_start_time,s);
    // fprintf(stderr,"%06d: %4d:  %s",my_world_rank,linenum,s);
    fflush(stderr);
    free(s);
}

// int buf_for_failed_dmalloc[IBUF_NUMINTS];
void *dmalloc(int nbytes, const char *funcname, int linenum)
{
    void *ptr;

    if ((curr_bytes_dmalloced+nbytes) > max_malloc)
    {
        aprintf(1,"** dmalloc aborting; exceeding mem limit %.0f ; "
                  "curr_bytes_dmalloced %.0f ;  nbytes %d ; from func %s line %d\n",
                max_malloc,curr_bytes_dmalloced,nbytes,funcname,linenum);
        print_proc_self_status();
        print_curr_mem_and_queue_status();
        adlb_server_abort(-1,1);
    }

    ptr = malloc(nbytes);
    if ( ! ptr)
    {
        aprintf(1,"** dmalloc aborting the pgm; failed for %d bytes in %s at line %d\n",
                nbytes,funcname,linenum);
        print_proc_self_status();
        print_curr_mem_and_queue_status();
        adlb_server_abort(-1,1);
    }

    total_bytes_dmalloced += nbytes;
    curr_bytes_dmalloced += nbytes;
    if (curr_bytes_dmalloced > hwm_bytes_dmalloced)
        hwm_bytes_dmalloced = curr_bytes_dmalloced;
    return ptr;
}

void dfree(void *ptr, int nbytes, const char *funcname, int linenum)
{
    free(ptr);
    curr_bytes_dmalloced -= nbytes;
}

static int get_type_idx(int work_type)
{
    int i;

    for (i=0; i < num_types; i++)
        if (user_types[i] == work_type)
            return i;
    aprintf(1,"**** INVALID type %d *************************************\n",work_type);
    return -1;
}

static int find_cand_rank_with_worktype(int for_rank, int work_type)
{
    int i, j, hi_prio, bsf_rank, server_rank, type_idx;
    xq_node_t *tq_node;
    tq_struct_t *ts;

    tq_node = tq_find_first_rt(for_rank,work_type);
    if (tq_node)
    {
        ts = tq_node->data;
        return ts->remote_server_rank;
    }
    bsf_rank = -1;
    hi_prio = ADLB_LOWEST_PRIO;
    for (i=0; i < num_servers; i++)
    {
        server_rank = master_server_rank + i;
        if (server_rank == my_world_rank)
            continue;
        if (rfr_out[server_rank])
            continue;
        if (qmstat_tbl[i].qlen_unpin_untarg > 0)
        {
            if (work_type < 0)
            {
                for (j=0; j < num_types; j++)
                {
                    if (qmstat_tbl[i].type_hi_prio[j] > hi_prio)
                    {
                        hi_prio = qmstat_tbl[i].type_hi_prio[j];
                        bsf_rank = server_rank;
                    }
                }
            }
            else
            {
                type_idx = get_type_idx(work_type);
                if (type_idx < 0) aprintf(1,"** invalid type\n");
                if (qmstat_tbl[i].type_hi_prio[type_idx] > hi_prio)
                {
                    hi_prio = qmstat_tbl[i].type_hi_prio[type_idx];
                    bsf_rank = server_rank;
                }
            }
        }
    }
    return bsf_rank;
}

static void check_remote_work_for_queued_apps()
{
    int i, j, cand_rank;
    int *temp_buf;
    xq_node_t *rq_node, *iq_node;
    rq_struct_t *rs;
    MPI_Request *temp_req;

    for (rq_node=xq_first(rq); rq_node; rq_node=xq_next(rq,rq_node))
    {
        rs = rq_node->data;
        if (rfr_to_rank[rs->world_rank] >= 0)
            continue;
        for (i=0; i < REQ_TYPE_VECT_SZ; i++)
        {
            if (rs->req_types[i] < -1)  /* invalid type place-holder */
                break;
            cand_rank = find_cand_rank_with_worktype(rs->world_rank,rs->req_types[i]);
            if (cand_rank >= 0)
            {
                aprintf(0000,"CAND_SERVER_RANK %06d type %d for %06d\n",cand_rank,rs->req_types[i],rs->world_rank);
                temp_buf    = amalloc(RFRBUF_NUMINTS * sizeof(int));
                temp_buf[0] = rs->rqseqno;
                temp_buf[1] = rs->world_rank;
                for (j=0; j < REQ_TYPE_VECT_SZ; j++)
                    temp_buf[2+j] = rs->req_types[j];
                temp_req    = amalloc(sizeof(MPI_Request));
                aprintf(0000,"REQING chk rqseqno %d fromrank %d\n",rs->rqseqno,cand_rank);
                // cblog(1,rs->world_rank,"  REQING from chk %d ty %d\n",cand_rank,rs->req_types[i]);
                MPI_Isend(temp_buf,RFRBUF_NUMINTS,MPI_INT,cand_rank,SS_RFR,
                          adlb_all_comm,temp_req);
                iq_node = iq_node_create(temp_req,(RFRBUF_NUMINTS * sizeof(int)),temp_buf);
                iq_append(iq_node);
                rfr_to_rank[rs->world_rank] = cand_rank;
                rfr_out[cand_rank] = 1;
                nrfrs_sent++;
                if (use_dbg_prints)
                    dbg_rfr_sent_cnt[rs->world_rank]++;
                aprintf(0000,"IN CHECK_REMOTE SENT REQ to %06d type %d for %06d\n",cand_rank,rs->req_types[i],rs->world_rank);
                break;
            }
        }
    }
}

static void update_local_state()
{
    int i, server_idx;

    server_idx  = get_server_idx(my_world_rank);
    qmstat_tbl[server_idx].nbytes_used = curr_bytes_dmalloced;
    qmstat_tbl[server_idx].qlen_unpin_untarg = wq_get_num_unpinned_untargeted();
    for (i=0; i < num_types; i++)
    {
        /* avail -> only not pinned and not targeted */
        qmstat_tbl[server_idx].type_hi_prio[i] = wq_get_avail_hi_prio_of_type(user_types[i]);
    }
}

static int get_server_idx(int server_rank)
{
    return server_rank - master_server_rank;
}

static int get_server_rank(int server_idx)
{
    return master_server_rank + server_idx;
}

void adlb_exit_handler()
{
    aprintf(1,"begin adlb_exit_handler:\n");
    print_proc_self_status();
    print_curr_mem_and_queue_status();
    aprintf(1,"  nputmsgs %d  nqmstatmsgs %d\n",nputmsgs,nqmstatmsgs);

    /* this ifdef is a temporary method for checking for sicortex (from David Gingold) */
#   if defined(linux) && defined(mips)
    aprintf(1,"sicortex mpidi queue values\n");
    aprintf(1,"    early queue len %d\n",MPIDI_Debug_early_queue_length());
    aprintf(1,"    recv  queue len %d\n",MPIDI_Debug_recv_queue_length());
    // aprintf(1,"dumping sicortex queue:\n");  /* can produce lots of output */
    // MPIDI_Early_dump_queue();
#   endif

    aprintf(1,"end adlb_exit_handler:\n");
}

/*
static int random_in_range(int lo, int hi)
{
    return ( lo + random() / (RAND_MAX / (hi - lo + 1) + 1) );
}
*/

/*
static int dump_qmstat_info()
{
    int i,j;

    for (i=0; i < num_servers; i++)
    {
        printf("%d wqlen  %d\n",get_server_rank(i),qmstat_tbl[i].qlen_unpin_untarg);
        printf("%d nbytes %.0f\n",get_server_rank(i),qmstat_tbl[i].nbytes_used);
    }
    for (i=0; i < num_servers; i++)
        for (j=0; j < num_types; j++)
            printf("%d type %d prio %d\n",
                   get_server_rank(i),user_types[j],qmstat_tbl[i].type_hi_prio[j]);
    return 0;
}
*/

#ifdef DEBUGGING_BGP
int GetUnexpectedRequestCount( void )
{
    void       *rreq;  /* MPID_Request */
    ptrdiff_t   mpitag_offset = 84;
    ptrdiff_t   mpirank_offset = 88;
    ptrdiff_t   mpictxt_offset = 92;
    ptrdiff_t   uebuflen_offset = 148;
#if MPICH2_NUMVERSION < 10100300
/* This corresponds to V1R3 and V1R2. */
    ptrdiff_t   next_offset = 704;
#else
/* This corresponds to V1R4. */
    ptrdiff_t   next_offset = 128;
#endif
    int         count;

    count = 0;
    rreq = *MPID_Recvq_unexpected_head_ptr;
    while (rreq != NULL) {
        count++;
        /*
        printf( "unexpected req %d, MPItag=%d, MPIrank=%d, MPIctxt=%"PRIu16
                ", uebuflen=%u\n",
                count,
                // MPID_Request_getMatchTag(rreq),
                *(unsigned*) (rreq + mpitag_offset),
                // MPID_Request_getMatchRank(rreq),
                *(unsigned*) (rreq + mpirank_offset),
                // MPID_Request_getMatchCtxt(rreq),
                *(uint16_t*) (rreq + mpictxt_offset),
                // rreq->dcmf.uebuflen
                *(unsigned*) (rreq + uebuflen_offset) );
        */
        /* rreq = rreq->dcmf.next; */
        rreq = *(void **)(rreq + next_offset);
    }
    return count;
}
void GetUnexpectedRequestTagsInDBGTagsBuf(int dbg_unexpected_by_tag[])
{
    void       *rreq;  /* MPID_Request */
    ptrdiff_t   mpitag_offset = 84;
#if MPICH2_NUMVERSION < 10100300
/* This corresponds to V1R3 and V1R2. */
    ptrdiff_t   next_offset = 704;
#else
/* This corresponds to V1R4. */
    ptrdiff_t   next_offset = 128;
#endif
    int         tag;

    rreq = *MPID_Recvq_unexpected_head_ptr;
    while (rreq != NULL)
    {
        tag = *(unsigned*) (rreq + mpitag_offset);
        if (tag > 1000)
            dbg_unexpected_by_tag[tag-1000]++;
        rreq = *(void **)(rreq + next_offset);
    }
}
#endif
