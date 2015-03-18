
/*
 * location.h
 *
 *  Implements location-aware features.
 *
 *  Created on: Feb 4, 2015
 *      Author: wozniak
 */

#ifndef HOSTMAP_H
#define HOSTMAP_H

#include <stdbool.h>

// Workaround for recursive dependency with common.h
struct xlb_layout;

/*
 * Structure mapping ranks to hosts
 */
struct xlb_hostnames {
  /** Maximum length of names */
  size_t name_length;

  /** All names as big array */
  char *all_names; 

  /** This rank's host name */
  char *my_name;
};

/*
 * Opaque structure mapping hosts to list of ranks
 */
struct xlb_hostmap;

typedef enum
{
  HOSTMAP_DISABLED,
  HOSTMAP_LEADERS,
  HOSTMAP_ENABLED
} xlb_hostmap_mode;

/** 
  Get configured hostmap mode from environment
 */
adlb_code
xlb_get_hostmap_mode(xlb_hostmap_mode *mode);

/**
  Build rank to hostname map.
  Must be called on all ranks in comm.
 */
adlb_code
xlb_hostnames_gather(MPI_Comm comm, struct xlb_hostnames *hostnames);

/**
  Note: no error checking, must make sure rank is valid
 */
const char *
xlb_hostnames_lookup(const struct xlb_hostnames *hostnames, int rank);

void
xlb_hostnames_free(struct xlb_hostnames *hostnames);

adlb_code
xlb_hostmap_init(const struct xlb_layout *layout,
                 const struct xlb_hostnames *hostnames,
                 struct xlb_hostmap **hostmap);

void xlb_hostmap_free(struct xlb_hostmap *hostmap);

/**
  Setup leader per node
 */
adlb_code
xlb_setup_leaders(struct xlb_layout *layout, struct xlb_hostmap *hosts,
                  MPI_Comm comm, MPI_Comm *leader_comm);

#endif
