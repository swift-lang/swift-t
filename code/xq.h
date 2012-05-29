#include <stdio.h>
#include <stdlib.h>
#include <string.h>


typedef struct xq_node_t
{
    struct xq_node_t *next;
    struct xq_node_t *prev;
    void *data;
} xq_node_t;

typedef struct xq_t
{
    xq_node_t termnode;
    int count;
    int max_count;
} xq_t;

xq_t *xq_create(void);
void xq_destroy(xq_t *xq);
xq_t *xq_init(xq_t *xq);
void xq_insert_after(xq_t *xq, xq_node_t *n, xq_node_t *after);
void xq_insert_before(xq_t *xq, xq_node_t *n, xq_node_t *before);
xq_node_t *xq_node_create(void *data);
void xq_delete(xq_t *xq, xq_node_t *xn);
void xq_append(xq_t *xq, xq_node_t *xn);
void xq_prepend(xq_t *xq, xq_node_t *xn);
xq_node_t *xq_first(xq_t *xq);
xq_node_t *xq_next(xq_t *xq, xq_node_t *xn);
xq_node_t *xq_prev(xq_t *xq, xq_node_t *xn);

/* adlb-specific code here */

#include "mpi.h"

#ifndef NDEBUG
#define aprintf(flag,...) adlbp_dbgprintf(flag,__LINE__,__VA_ARGS__)
#else
#define aprintf(flag,...) // noop
#endif
void *dmalloc(int,const char *,int);
#define amalloc(nbytes)   dmalloc(nbytes,__FUNCTION__,__LINE__)
void dfree(void *,int,const char *,int);
#define afree(ptr,nbytes) dfree(ptr,nbytes,__FUNCTION__,__LINE__)

#define  REQ_TYPE_VECT_SZ                    16

typedef struct wq_struct_t
{
    int target_rank;
    int temp_target_rank;  /* used during push */
    int home_server_rank;
    int pin_rank;
    int pinned;
    int work_type;
    int work_prio;
    int work_len;
    int answer_rank;
    int wqseqno;
    int common_len;
    int common_server_rank;
    int common_server_commseqno;
    void *work_buf;
    double time_stamp;
} wq_struct_t;

typedef struct rq_struct_t
{
    double time_stamp;
    int world_rank;
    int rqseqno;
    int req_types[REQ_TYPE_VECT_SZ];
} rq_struct_t;

typedef struct iq_struct_t
{
    int buf_len;
    void *buf;
    MPI_Request *mpi_req;
} iq_struct_t;

typedef struct tq_struct_t
{
    int app_rank;
    int work_type;
    int remote_server_rank;
    int num_stored;
} tq_struct_t;

typedef struct cq_struct_t
{
    int cqseqno;
    int commlen;
    int refcnt;
    int ngets;
    void *buf;
} cq_struct_t;


xq_t *wq;
xq_t *rq;
xq_t *iq;
xq_t *tq;
xq_t *cq;

xq_node_t *wq_node_create(int work_type, int work_prio, int wqseqno, int answer_rank,
                          int target_rank, int work_len, void *work_buf);
void wq_append(xq_node_t *xn);
void wq_delete(xq_node_t *xn);
xq_node_t *wq_find_seqno(int wqseqno);
xq_node_t *wq_find_hi_prio(int *req_types);
xq_node_t *wq_find_pre_targeted_hi_prio(int target_rank, int *req_types);
xq_node_t *wq_find_pinned_for_rank(int target_rank, int wqseqno);
xq_node_t *wq_find_unpinned(void);
int wq_get_num_unpinned(void);
int wq_get_num_unpinned_untargeted(void);
int wq_get_avail_hi_prio_of_type(int work_type);
void wq_print_info(void);

xq_node_t *rq_node_create(int world_rank, int *req_types, int rqseqno);
void rq_append(xq_node_t *xn);
void rq_delete(xq_node_t *xn);
xq_node_t *rq_find_rank_queued_for_type(int rank, int work_type);
xq_node_t *rq_find_seqno(int rqseqno);
void rq_print_info(int num_types);

xq_node_t *iq_node_create(MPI_Request *mpi_req, int buf_len, void *buf);
void iq_append(xq_node_t *xn);
void iq_delete(xq_node_t *xn);
void iq_print_info(void);

xq_node_t *tq_node_create(int app_rank, int work_type, int remote_server_rank, int num_stored);
void tq_append(xq_node_t *xn);
void tq_delete(xq_node_t *xn);
xq_node_t *tq_find_first_rt(int for_rank,int work_type);
xq_node_t *tq_find_rtr(int for_rank,int work_type,int remote_server_rank);
void tq_print_info(void);

xq_node_t *cq_node_create(int commlen, void *common_data, int seqno);
void cq_append(xq_node_t *xn);
void cq_delete(xq_node_t *xn);
xq_node_t *cq_find_seqno(int cqseqno);
void cq_print_info(void);
