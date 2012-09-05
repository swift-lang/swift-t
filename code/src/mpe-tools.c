
/*
 * mpe-tools.c
 *
 *  Created on: Aug 29, 2012
 *      Author: wozniak
 */

#include <mpi.h>

#include "common.h"
#include "mpe-tools.h"

#ifdef ENABLE_MPE

int xlb_mpe_init_start, xlb_mpe_init_end;
int xlb_mpe_finalize_start, xlb_mpe_finalize_end;

int xlb_mpe_svr_put_start, xlb_mpe_svr_put_end;
int xlb_mpe_svr_get_start, xlb_mpe_svr_get_end;
int xlb_mpe_svr_steal_start, xlb_mpe_svr_steal_end;

int xlb_mpe_dmn_steal_start, xlb_mpe_dmn_steal_end;
int xlb_mpe_dmn_shutdown_start, xlb_mpe_dmn_shutdown_end;
int xlb_mpe_svr_shutdown_start, xlb_mpe_svr_shutdown_end;

int xlb_mpe_wkr_put_start, xlb_mpe_wkr_put_end;
int xlb_mpe_wkr_get_start, xlb_mpe_wkr_get_end;
int xlb_mpe_wkr_create_start, xlb_mpe_wkr_create_end;
int xlb_mpe_wkr_store_start, xlb_mpe_wkr_store_end;
int xlb_mpe_wkr_retrieve_start, xlb_mpe_wkr_retrieve_end;
int xlb_mpe_wkr_subscribe_start, xlb_mpe_wkr_subscribe_end;
int xlb_mpe_wkr_close_start, xlb_mpe_wkr_close_end;
int xlb_mpe_wkr_unique_start, xlb_mpe_wkr_unique_end;

void
xlb_mpe_setup()
{
  /* MPE_Init_log() & MPE_Finish_log() are NOT needed when liblmpe.a
        is linked because MPI_Init() would have called MPE_Init_log()
        already. */
  MPE_Init_log();

  make_pair(init);
  make_pair(finalize);

  make_pair(svr_put);
  make_pair(svr_get);
  make_pair(svr_steal);
  make_pair(svr_shutdown);

  make_pair(dmn_steal);
  make_pair(dmn_shutdown);

  make_pair(wkr_put);
  make_pair(wkr_get);

  make_pair(wkr_unique);
  make_pair(wkr_create);
  make_pair(wkr_subscribe);
  make_pair(wkr_store);
  make_pair(wkr_close);
  make_pair(wkr_retrieve);

  int rank;
  MPI_Comm_rank(MPI_COMM_WORLD, &rank);

  if (rank == 0 ) {
    describe_pair(ADLB, init);
    describe_pair(ADLB, finalize);
    describe_pair(ADLB, wkr_put);
    describe_pair(ADLB, wkr_get);
    describe_pair(handler, svr_get);
    describe_pair(handler, svr_put);
    describe_pair(handler, svr_steal);
    describe_pair(handler, svr_shutdown);
    describe_pair(daemon, dmn_steal);
    describe_pair(daemon, dmn_shutdown);
    describe_pair(ADLB, wkr_create);
    describe_pair(ADLB, wkr_store);
    describe_pair(ADLB, wkr_retrieve);
    describe_pair(ADLB, wkr_subscribe);
    describe_pair(ADLB, wkr_close);
    describe_pair(ADLB, wkr_unique);
  }
}
#endif
