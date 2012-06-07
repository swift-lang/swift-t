#include <limits.h>  /* for INT_MIN */

#include "xq.h"

xq_t *xq_create()
{
    xq_t *xq;

    xq = (xq_t *) amalloc(sizeof(xq_t));
    if ( ! xq )
        return NULL;
    xq_init(xq);
    return xq;
}

void xq_destroy(xq_t *xq)
{
    afree(xq,sizeof(xq_t));  /* assumes user has deleted all entries */
}

xq_t *xq_init(xq_t *xq)
{
    xq->termnode.next = &xq->termnode;
    xq->termnode.prev = &xq->termnode;
    xq->count = 0;
    xq->max_count = 0;
    return xq;
}

void xq_insert_after(xq_t *xq, xq_node_t *xn, xq_node_t *after)
{
    xn->prev = after;
    xn->next = after->next;
    after->next->prev = xn;
    after->next = xn;
    xq->count++;
    if (xq->count > xq->max_count)
        xq->max_count = xq->count;
}

void xq_insert_before(xq_t *xq, xq_node_t *xn, xq_node_t *before)
{
    xq_insert_after(xq, xn, before->prev);
}

xq_node_t *xq_node_create(void *data)
{
    xq_node_t *xn;

    xn = (xq_node_t *) amalloc(sizeof(xq_node_t));
    if ( ! xn )
        return NULL;
    xn->data = data;
    xn->next = NULL;
    xn->prev = NULL;
    return xn;
}

void xq_delete(xq_t *xq, xq_node_t *xn)
{
    xn->prev->next = xn->next;
    xn->next->prev = xn->prev;
    xn->next = NULL;
    xn->prev = NULL;
    xq->count--;
    afree(xn,sizeof(xq_node_t));
}

void xq_append(xq_t *xq, xq_node_t *xn)
{
    xq_insert_before(xq, xn, &xq->termnode);
}

void xq_prepend(xq_t *xq, xq_node_t *xn)
{
    xq_insert_after(xq, xn, &xq->termnode);
}

xq_node_t *xq_first(xq_t *xq)
{
    return xq_next(xq, &(xq->termnode));
}

xq_node_t *xq_next(xq_t *xq, xq_node_t *xn)
{
    if (xn->next != &(xq->termnode))
        return xn->next;
    else
        return NULL;
}

xq_node_t *xq_prev(xq_t *xq, xq_node_t *xn)
{
    if (xn->prev != &(xq->termnode))
        return xn->prev;
    else
        return NULL;
}


/* adlb-specific code here */

/* wq stuff */

/* must match adlb */
#define ADLB_LOWEST_PRIO  INT_MIN

xq_node_t *wq_node_create(int work_type, int work_prio, int wqseqno, int answer_rank,
                          int target_rank, int work_len, void *work_buf)
{
    xq_node_t *xn;
    wq_struct_t *ws;

    ws = amalloc(sizeof(wq_struct_t));
    if ( ! ws )
        return NULL;
    xn = xq_node_create(ws);
    if ( ! xn )
        return NULL;
    /****
    ws->work_buf = amalloc(work_len);
    if ( ! ws->work_buf )
        return NULL;
    memcpy(ws->work_buf,work_buf,work_len);
    ****/
    ws->work_buf          = work_buf;
    ws->work_type         = work_type;
    ws->work_prio         = work_prio;
    ws->work_len          = work_len;
    ws->answer_rank       = answer_rank;
    ws->wqseqno           = wqseqno;
    ws->target_rank       = target_rank;
    ws->home_server_rank  = -1;  /* chgd outside if needed for target rank */
    ws->pin_rank          = -1;  /* chgd outside as nec */
    ws->pinned            = 0;
    ws->common_len        = 0;
    ws->common_server_rank = -1;
    ws->common_server_commseqno = -1;
    ws->time_stamp        = 0;    /* chgd outside */
    return xn;    /* really returning an xq node */
}

void wq_append(xq_node_t *xn)
{
    xq_insert_before(wq, xn, &wq->termnode);
}

void wq_delete(xq_node_t *xn)
{
    wq_struct_t *ws;

    ws = (wq_struct_t *) xn->data;
    if (ws)
    {
        if ( ws->work_buf )
        {
            afree(ws->work_buf,ws->work_len);
        }
        afree(ws,sizeof(wq_struct_t));
    }
    xq_delete(wq,xn);
}

xq_node_t *wq_find_seqno(int wqseqno)
{
    xq_node_t *xn;
    wq_struct_t *ws;

    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if (ws->wqseqno == wqseqno)
            return xn;
    }
    return NULL;
}

xq_node_t *wq_find_hi_prio(int *req_types)
{
    int i, hi_prio = -999999999;
    xq_node_t *xn, *bsf = NULL;
    wq_struct_t *ws;

    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if (ws->pinned)    // PTW: don't grab pinned data
            continue;
        if (ws->target_rank < 0)   /* if NOT targeted */
        {
            for (i=0; i < REQ_TYPE_VECT_SZ; i++)
            {
                if (req_types[i] == -1  ||  req_types[i] == ws->work_type)
                {
                    if (ws->work_prio > hi_prio)
                    {
                        hi_prio = ws->work_prio;
                        bsf = xn;
                    }
                }
            }
        }
    }
    return bsf;
}

xq_node_t *wq_find_pre_targeted_hi_prio(int target_rank, int *req_types)
{
    int i, hi_prio;
    xq_node_t *xn, *bsf = NULL;
    wq_struct_t *ws;

    hi_prio = ADLB_LOWEST_PRIO;
    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if (ws->pinned)    // PTW: don't grab pinned data
            continue;
        if (ws->target_rank == target_rank)
        {
            for (i=0; i < REQ_TYPE_VECT_SZ; i++)
            {
                if (req_types[i] == -1  ||  req_types[i] == ws->work_type)
                {
                    if (ws->work_prio > hi_prio)
                    {
                        hi_prio = ws->work_prio;
                        bsf = xn;
                    }
                }
            }
        }
    }
    return bsf;
}

xq_node_t *wq_find_pinned_for_rank(int pin_rank, int wqseqno)
{
    xq_node_t *xn;
    wq_struct_t *ws;

    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if (ws->pin_rank == pin_rank  &&  ws->wqseqno == wqseqno)
            break;
    }
    if ( ! xn  ||  xn == &(wq->termnode))
        return NULL;
    else
        return xn;
}

xq_node_t *wq_find_unpinned()
{
    xq_node_t *xn;
    wq_struct_t *ws;

    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if ( ! ws->pinned)
            break;
    }
    if ( ! xn  ||  xn == &(wq->termnode))
        return NULL;
    else
        return xn;
}

int wq_get_num_unpinned()
{
    xq_node_t *xn;
    wq_struct_t *ws;
    int num_unpinned = 0;

    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if ( ! ws->pinned)
            num_unpinned++;
    }
    return num_unpinned;
}

int wq_get_num_unpinned_untargeted()
{
    xq_node_t *xn;
    wq_struct_t *ws;
    int num = 0;

    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if ( ! ws->pinned  &&  ws->target_rank < 0)
            num++;
    }
    return num;
}

int wq_get_avail_hi_prio_of_type(int work_type)  /* avail -> only not pinned and not targeted */
{
    xq_node_t *xn;
    wq_struct_t *ws;
    int hi_prio;

    hi_prio = ADLB_LOWEST_PRIO;
    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = (wq_struct_t *) xn->data;
        if (ws->pinned  ||  ws->target_rank >= 0)
            continue;
        if (ws->work_type == work_type  &&  ws->work_prio > hi_prio)
            hi_prio = ws->work_prio;
    }
    return hi_prio;
}

void adlbp_dbgprintf(int flag, int linenum, char *fmt, ...);

void wq_print_info()
{
    double wq_nbytes;
    xq_node_t *xn;
    wq_struct_t *ws;

    aprintf(1,"wq has %d entries:\n",wq->count);
    wq_nbytes = 0;
    for (xn=xq_first(wq);  xn && xn != &(wq->termnode);  xn=xn->next)
    {
        ws = xn->data;
        wq_nbytes += ws->work_len;
        aprintf(0,"    wq_entry: work_type %d  targ_rank %d  home_server_rank %d\n",
                ws->work_type,ws->target_rank,ws->home_server_rank);
    }
    aprintf(1,"    wq size in bytes %.0f\n",wq_nbytes);
}


/* rq stuff */

xq_node_t *rq_node_create(int world_rank, int *req_types, int rqseqno)
{
    int i;
    rq_struct_t *rs;
    xq_node_t *xn;

    rs = amalloc(sizeof(rq_struct_t));
    if ( ! rs )
        return NULL;
    xn = xq_node_create(rs);
    if ( ! xn )
        return NULL;
    rs->world_rank = world_rank;
    for (i=0; i < REQ_TYPE_VECT_SZ; i++)
        rs->req_types[i] = req_types[i];
    rs->rqseqno = rqseqno;
    rs->time_stamp = 0;    /* chgd outside */
    return xn;
}

void rq_append(xq_node_t *xn)
{
    rq_struct_t *rs = (rq_struct_t *) xn->data;  /* useful for debug printing */

    xq_insert_before(rq, xn, &rq->termnode);
}

void rq_delete(xq_node_t *xn)
{
    if (xn->data)
    {
        afree((rq_struct_t *)xn->data,sizeof(rq_struct_t));
    }
    xq_delete(rq,xn);
}

xq_node_t *rq_find_rank_queued_for_type(int rank, int work_type)  /* either arg may be -1 */
{
    int i;
    xq_node_t *xn;
    rq_struct_t *rs;

    for (xn=xq_first(rq);  xn && xn != &(rq->termnode);  xn=xn->next)
    {
        rs = (rq_struct_t *) xn->data;
        for (i=0; i < REQ_TYPE_VECT_SZ; i++)
        {
            if (work_type == -1 ||  rs->req_types[i] == -1 ||  work_type == rs->req_types[i])
                if (rank == -1  ||  rank == rs->world_rank)
                    return xn;
        }
    }
    return NULL;
}

xq_node_t *rq_find_seqno(int rqseqno)
{
    xq_node_t *xn;
    rq_struct_t *rs;

    for (xn=xq_first(rq);  xn && xn != &(rq->termnode);  xn=xn->next)
    {
        rs = (rq_struct_t *) xn->data;
        if (rs->rqseqno == rqseqno)
            return xn;
    }
    return NULL;
}

void rq_print_info(int num_types)
{
    int i;
    xq_node_t *xn;
    rq_struct_t *rs;
    char temp_buf[64], debug_buf[4096];

    aprintf(1,"rq has %d entries:\n",rq->count);
    for (xn=xq_first(rq);  xn && xn != &(rq->termnode);  xn=xn->next)
    {
        rs = (rq_struct_t *) xn->data;
        debug_buf[0] = '\0';
        for (i=0; i < num_types; i++)
        {
            if (rs->req_types[i] < 0  &&  i > 0)
                break;
            sprintf(temp_buf,"%d ",rs->req_types[i]);
            if ((strlen(debug_buf) + strlen(temp_buf)) > 4096)
                break;
            strcat(debug_buf,temp_buf);
        }
        aprintf(1,"    rq_entry: rank %d  types %s\n",rs->world_rank,debug_buf);
    }
}


/* iq stuff */

xq_node_t *iq_node_create(MPI_Request *mpi_req, int buf_len, void *buf)
{
    xq_node_t *xn;
    iq_struct_t *is;

    is = amalloc(sizeof(iq_struct_t));
    if ( ! is )
        return NULL;
    xn = xq_node_create(is);
    if ( ! xn )
        return NULL;
    is->mpi_req = mpi_req;
    is->buf_len = buf_len;
    is->buf = buf;
    return xn;
}

void iq_append(xq_node_t *xn)
{
    xq_insert_before(iq, xn, &iq->termnode);
}

void iq_delete(xq_node_t *xn)
{
    if (xn->data)
    {
        if ( ((iq_struct_t *)xn->data)->buf )
        {
            afree( ((iq_struct_t *)xn->data)->buf, ((iq_struct_t *)xn->data)->buf_len );
        }
        if ( ((iq_struct_t *)xn->data)->mpi_req )
        {
            afree( ((iq_struct_t *)xn->data)->mpi_req, sizeof(MPI_Request) );
        }
        afree((iq_struct_t *)xn->data,sizeof(iq_struct_t));
    }
    xq_delete(iq,xn);
}

void iq_print_info()
{
    int iq_nbytes;
    xq_node_t *xn;
    iq_struct_t *is;

    iq_nbytes = 0;
    for (xn=xq_first(iq);  xn && xn != &(iq->termnode);  xn=xn->next)
    {
        is = xn->data;
        iq_nbytes += is->buf_len;
        iq_nbytes += sizeof(MPI_Request);
    }
    aprintf(1,"iq has %d entries;  size in bytes %d\n",iq->count,iq_nbytes);
}


/* tq stuff */  /* targeted work queue */

xq_node_t *tq_node_create(int app_rank, int work_type, int remote_server_rank, int num_stored)
{
    xq_node_t *xn;
    tq_struct_t *ts;

    ts = amalloc(sizeof(tq_struct_t));
    if ( ! ts )
        return NULL;
    xn = xq_node_create(ts);
    if ( ! xn )
        return NULL;
    ts->app_rank = app_rank;
    ts->work_type = work_type;
    ts->remote_server_rank = remote_server_rank;
    ts->num_stored = num_stored;
    return xn;
}

void tq_append(xq_node_t *xn)
{
    xq_insert_before(tq, xn, &tq->termnode);
}

void tq_delete(xq_node_t *xn)
{
    if (xn->data)
    {
        afree((tq_struct_t *)xn->data,sizeof(tq_struct_t));
    }
    xq_delete(tq,xn);
}

xq_node_t *tq_find_first_rt(int for_rank,int work_type)
{
    xq_node_t *xn;
    tq_struct_t *ts;

    for (xn=xq_first(tq);  xn && xn != &(tq->termnode);  xn=xn->next)
    {
        ts = (tq_struct_t *) xn->data;
        if (for_rank == ts->app_rank)
        {
            if (work_type == -1  ||  work_type == ts->work_type)
                return xn;
        }
    }
    return NULL;
}

xq_node_t *tq_find_rtr(int for_rank,int work_type,int remote_server_rank)
{
    xq_node_t *xn;
    tq_struct_t *ts;

    for (xn=xq_first(tq);  xn && xn != &(tq->termnode);  xn=xn->next)
    {
        ts = (tq_struct_t *) xn->data;
        if (ts->app_rank == for_rank  &&  ts->work_type == work_type  &&
            ts->remote_server_rank == remote_server_rank)
        {
            return xn;
        }
    }
    return NULL;
}

void tq_print_info()
{
    xq_node_t *xn;
    tq_struct_t *ts;

    aprintf(1,"tq has %d entries:\n",tq->count);
    for (xn=xq_first(tq);  xn && xn != &(tq->termnode);  xn=xn->next)
    {
        ts = (tq_struct_t *) xn->data;
        aprintf(1,"  tq_entry:  app_rank %d ty %d remote_server %d num %d\n",
                ts->app_rank,ts->work_type,ts->remote_server_rank,ts->num_stored);
    }
}

/* cq stuff */  /* common data chunks */

xq_node_t *cq_node_create(int commlen, void *commbuf, int seqno)
{
    xq_node_t *xn;
    cq_struct_t *cs;

    cs = amalloc(sizeof(cq_struct_t));
    if ( ! cs )
        return NULL;
    xn = xq_node_create(cs);
    if ( ! xn )
        return NULL;
    cs->commlen = commlen;
    cs->buf     = commbuf;
    cs->cqseqno = seqno;
    cs->refcnt  = -1;  /* to be re-set later at end of batch */
    cs->ngets   = 0;   /* incremented at each get */
    return xn;
}

void cq_delete(xq_node_t *xn)
{
    cq_struct_t *cs;

    cs = (cq_struct_t *) xn->data;
    afree(cs->buf,cs->commlen);
    afree(cs,sizeof(cq_struct_t));
    xq_delete(cq,xn);
}

void cq_append(xq_node_t *xn)
{
    xq_insert_before(cq, xn, &cq->termnode);
}

xq_node_t *cq_find_seqno(int cqseqno)
{
    xq_node_t *xn;
    cq_struct_t *cs;

    for (xn=xq_first(cq);  xn && xn != &(cq->termnode);  xn=xn->next)
    {
        cs = (cq_struct_t *) xn->data;
        if (cs->cqseqno == cqseqno)
            return xn;
    }
    return NULL;
}

void cq_print_info()
{
    double cq_nbytes;
    xq_node_t *xn;
    cq_struct_t *cs;

    aprintf(1,"cq has %d entries:\n",cq->count);
    cq_nbytes = 0;
    for (xn=xq_first(cq);  xn && xn != &(cq->termnode);  xn=xn->next)
    {
        cs = xn->data;
        cq_nbytes += cs->commlen;
        aprintf(1,"    cq_entry: commlen %d  refcnt %d  ngets %d  refcnt-ngets %d\n",
                cs->commlen,cs->refcnt,cs->ngets,cs->refcnt-cs->ngets);
    }
    aprintf(1,"    cq total commlen in bytes %.0f\n",cq_nbytes);
}
