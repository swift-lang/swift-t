
/*
 * location.h
 *
 *  Implements location-aware features.
 *
 *  Created on: Feb 4, 2015
 *      Author: wozniak
 */

#pragma once

#include <stdbool.h>

#include "common.h"
#include "layout-defs.h"

/*
 * Structure mapping ranks to hosts
 */
struct xlb_hostnames {
  /** Maximum length of names */
  size_t name_length;

  /** All names as big array */
  char* all_names;

  /** This rank's host name */
  char* my_name;
};

/*
 * Opaque structure mapping hosts to list of ranks
 */
struct xlb_hostmap;

/**
  Get configured hostmap mode from environment
 */
adlb_code
xlb_get_hostmap_mode(void);
// struct xlb_state* xlb_s

/**
  Build rank to hostname map.
  Must be called on all ranks in comm.
 */
adlb_code
xlb_hostnames_gather(MPI_Comm comm, struct xlb_hostnames *hostnames);

/**
  Fill hostnames structure with hostname per rank
 */
adlb_code
xlb_hostnames_fill(struct xlb_hostnames *hostnames,
              const char **names, int nranks, int my_rank);

/**
  Note: no error checking, must make sure rank is valid
 */
const char *
xlb_hostnames_lookup(const struct xlb_hostnames *hostnames, int rank);

void
xlb_hostnames_free(struct xlb_hostnames *hostnames);

adlb_code xlb_hostmap_init(const struct xlb_hostnames* hostnames);

void xlb_hostmap_free(void);
// struct xlb_hostmap *hostmap

/**
  Setup leader per node
 */
adlb_code
xlb_setup_leaders(// xlb_layout *layout, struct xlb_hostmap *hosts,
                  MPI_Comm comm, MPI_Comm *leader_comm);

/**
   List ranks in ADLB comm that are in the leader comm
   @param setenvs: If true, set environment variables for user code
                   This only need be done at initialization time
   @return results in leader_ranks, which should be preallocated to hold ints
                   for all of the ADLB comm.  The leader rank count will go in count
 */
void xlb_get_leader_ranks(// xlb_layout* layout, struct xlb_hostmap* hosts,
                          bool setenvs, int* leader_ranks, int* count);
