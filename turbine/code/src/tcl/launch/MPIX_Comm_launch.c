#define _GNU_SOURCE // for strchrnul()
#include <assert.h>

#include <errno.h>
#include <limits.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <unistd.h>

// If this system does not have strchrnul(),
// see ExM c-utils strchrnul.h
#include <config.h>
#include <strchrnul.h>

#include "MPIX_Comm_launch.h"

static char* old_pwd = NULL;

static char* info_get_launcher(MPI_Info info) {
	char* launcher; int flag = 0;
	if(MPI_INFO_NULL != info) {
		int len = 0;
		MPI_Info_get_valuelen(info, "launcher", &len, &flag);
		if(flag) {
			launcher = (char*)malloc(sizeof(char)*(len+1));
			MPI_Info_get(info,"launcher",len+1,launcher,&flag);
		}
	}
	if(!flag) {
		launcher = (char*)malloc(sizeof(char)*(strlen(MPI_DEFAULT_LAUNCHER)+1));
		strcpy(launcher, MPI_DEFAULT_LAUNCHER);
	}
	return launcher;
}

static char* info_get_launcher_options(MPI_Info info) {
	char* result; int flag = 0;
	if(MPI_INFO_NULL != info) {
		int len = 0;
		MPI_Info_get_valuelen(info, "options", &len, &flag);
		if(flag) {
			result = (char*)malloc(sizeof(char)*(len+1));
			MPI_Info_get(info,"options",len+1,result,&flag);
		}
	}
	if(!flag) {
		result = (char*)malloc(sizeof(char)*1);
		strcpy(result, "");
	}
	return result;
}


static char* info_get_output_redirection(MPI_Info info) {
	char* redirect; int flag = 0;
	if(MPI_INFO_NULL != info) {
		int len = 0;
		MPI_Info_get_valuelen(info, "output", &len, &flag);
		if(flag) {
			char filename[len+1];

			MPI_Info_get(info,"output",len+1,filename,&flag);
			redirect = (char*)malloc(sizeof(char)*(len+16));
			sprintf(redirect,"1> %s 2>&1",filename);
		}
	}
	if(!flag) {
		redirect = (char*)malloc(sizeof(char));
		redirect[0] = '\0';
	}
	return redirect;
}

static void info_get_envs_error(MPI_Comm comm, const char* message);

/**
 * Return 1 on success, 0 on error.
 * OUT: envs, envs_length
 * If there are no environment variables, *envs=NULL, envs_length=0.
 * Reads info object, puts all environment variables in envs,
 * envs_length is number of environment variables found.
 * */
static int info_get_envs(MPI_Comm comm, MPI_Info info,
		char** envs, size_t* envs_length) {
	int flag = 0;
	char* result;
	if(MPI_INFO_NULL == info) {
		*envs = NULL;
		*envs_length = 0;
		return 1;
	}
	int len = 0;
	MPI_Info_get_valuelen(info, "envs", &len, &flag);
	if(!flag) {
		*envs = NULL;
		*envs_length = 0;
		return 1;
	}
	char count_string[len+1];
	MPI_Info_get(info,"envs",len+1,count_string,&flag);
	long count = strtod(count_string, NULL);
	int* lengths = alloca(count * sizeof(int));
	char* env_word = "env  ";
	size_t env_word_length = strlen(env_word);
	size_t total = env_word_length;
	char key[16];
	int i;
	for(i=0; i<count; i++) {
		sprintf(key, "env%i", i);
		MPI_Info_get_valuelen(info, key, &len, &flag);
		if(!flag) info_get_envs_error(comm, key);
		lengths[i] = len;
		total += len+2;
	}
	result = malloc(total+1);
	strcpy(result, env_word);
	char* p = result+env_word_length;
	for(i=0; i<count; i++) {
		sprintf(key, "env%i", i);
		MPI_Info_get(info,key,lengths[i],p,&flag);
		p += lengths[i];
		*p = ' ';
		p++;
	}
	*p = '\0';

	*envs = result;
	*envs_length = strlen(result);
	return 1;
}

static void info_get_envs_error(MPI_Comm comm, const char* key) {
	printf("MPIX_Comm_launch(): error retrieving info key: %s\n", key);
	MPI_Abort(comm, 1);
}

static double TIMEOUT_NONE = -400;

/**
   Obtain info key "timeout", which times out the task.
   Hydra users can also use MPIEXEC_TIMEOUT
 */
static double info_get_timeout(MPI_Comm comm, MPI_Info info) {
	float timeout = (float) TIMEOUT_NONE; int flag = 0;
	if(MPI_INFO_NULL != info) {
		int len;
		MPI_Info_get_valuelen(info, "timeout", &len, &flag);
		if(flag) {
			char timeout_string[len+1];
			MPI_Info_get(info,"timeout",len+1,timeout_string,&flag);
			int n = sscanf(timeout_string, "%f", &timeout);
			if (n != 1) {
				printf("MPIX_Comm_launch(): error retrieving info value for key 'timeout': "
						"should be a double: '%s'\n", timeout_string);
				MPI_Abort(comm, 1);
			}
		}
	}
	return timeout;
}

static inline void chdir_checked(MPI_Comm comm, const char* d);

static void info_chdir(MPI_Comm comm, MPI_Info info) {

	// Did the user set chdir=dir ?
	if(MPI_INFO_NULL == info) return;
	int flag, len;
	MPI_Info_get_valuelen(info, "chdir", &len, &flag);
	if(!flag) return;

	// Save PWD
	old_pwd = getcwd(NULL, 0);

	// Do the chdir()
	char dir_string[len+1];
	MPI_Info_get(info,"chdir",len+1,dir_string,&flag);
	chdir_checked(comm, dir_string);
}

/**
  If the info key "write_hosts"=filename is given,
  write the hosts to the filename
*/
static int write_hosts(MPI_Info info, const char* allhosts, int size) {
	int len, flag=0;
	if(MPI_INFO_NULL == info) {
		return MPI_SUCCESS;
	}
        MPI_Info_get_valuelen(info, "write_hosts", &len, &flag);
	if(!flag) {
		return MPI_SUCCESS;
	}
	if(len > PATH_MAX) {
		return MPI_ERR_NAME;
	}
	char filename[len+1];
	MPI_Info_get(info,"write_hosts",len+1,filename,&flag);
	// printf("write_hosts=%s size=%i\n", filename, size);
	FILE* fp = fopen(filename, "w");
	if(fp == NULL) {
		return MPI_ERR_IO;
	}
	int i=0;
	const char* p = allhosts;
	for(i=0; i<size; i++) {
		const char* q = strchrnul(p, ',');
		fwrite(allhosts, q-p, 1, fp);
		fwrite("\n", 1, 1, fp);
		p = q+1;
	}
	fclose(fp);
	return MPI_SUCCESS;
}

/**
   Return true; false on invalid input
   If a slurm_bind setting set the mask, store it in *mask
   Else set *mask=NULL
*/
static bool
info_get_map(MPI_Info info, char** map)
{
  // Default:
  *map = NULL;
  int length, flag=0;
  if (info == MPI_INFO_NULL)
    return true;
  MPI_Info_get_valuelen(info, "slurm_bind", &length, &flag);
  if (!flag)
    return true;
  char* s = malloc((length+1) * sizeof(char));
  MPI_Info_get(info, "slurm_bind", length+1, s, &flag);
  assert(flag);
  *map = s;
  return true;
}

int turbine_MPIX_Comm_launch(const char* cmd, char** argv,
		MPI_Info info, int root, MPI_Comm comm,
		int* exit_code) {

	int r;
	// allhosts must be initialized before the gotos are reached
	char* allhosts = NULL;

	// get the name of this host
	char procname[MPI_MAX_PROCESSOR_NAME+1];
	memset(procname,0,MPI_MAX_PROCESSOR_NAME+1);
	int procname_len = MPI_MAX_PROCESSOR_NAME+1;
	r = MPI_Get_processor_name(procname, &procname_len);
	if(r) goto fn_error;

	// gather all names at process root;
	int size, rank;
	MPI_Comm_rank(comm,&rank);
	MPI_Comm_size(comm,&size);
	if(rank == root) {
		allhosts = (char*)malloc(sizeof(char)*(MPI_MAX_PROCESSOR_NAME+1)*size);
		if(!allhosts) goto fn_error;
	}
	r = MPI_Gather(procname, MPI_MAX_PROCESSOR_NAME+1, MPI_CHAR,
	               allhosts, MPI_MAX_PROCESSOR_NAME+1, MPI_CHAR,
	               root, comm);
	if(r) goto fn_error;

	// printf("exec\n");   fflush(stdout);
	if(rank == root) {

		// get the launcher
		char* launcher = info_get_launcher(info);
		char* launcher_options = info_get_launcher_options(info);
		printf("launcher_options: '%s'\n", launcher_options);
		// get output redirection string
		char* redirect = info_get_output_redirection(info);
		// get the timeout
		float timeout = (float) info_get_timeout(comm, info);
		info_chdir(comm, info);

		char timeout_string[64];
		int  timeout_string_length = 0;
		timeout_string[0] = '\0';
		if (timeout != TIMEOUT_NONE) {
			timeout_string_length = sprintf(timeout_string, "timeout %0.3f ", timeout);
		}

		int i, j = 0;
		// concatenate the host names into a comma-separated string
		// for mpiexec
		for(i=0; i<(MPI_MAX_PROCESSOR_NAME+1)*size; i++) {
			if(allhosts[i] != '\0') {
				allhosts[j] = allhosts[i];
				j += 1;
			} else {
				if(j > 0 && allhosts[j-1] != ',') {
					allhosts[j] = ',';
					j += 1;
				}
			}
		}
		allhosts[j-1] = '\0';

		// get the environment
		char* envs;
		size_t env_length;
		int rc = info_get_envs(comm, info, &envs, &env_length);
		assert(rc);

		write_hosts(info, allhosts, size);

		// compute the size of the string to pass to system()
		size_t s = strlen(cmd)+512;
		s += timeout_string_length;
		s += env_length;

		s += strlen(allhosts)+1;
		s += strlen(launcher)+1;
		s += strlen(redirect)+1;
		if(argv)
			for(i=0; argv[i] != NULL; i++)
				s += strlen(argv[i])+1;

		// allocate enough memory
		char* mpicmd = (char*)malloc(s*sizeof(char));
		if(!mpicmd) goto fn_error;
		memset(mpicmd,0,s);

		// create MPI command
		if(strcmp("turbine",launcher) == 0) {
			sprintf(mpicmd, "-hosts=%s", allhosts);
			setenv("TURBINE_LAUNCH_OPTIONS", mpicmd, 1);
			sprintf(mpicmd, "%s -n %d ", launcher, (size+1));
		} else {
			/* sprintf(mpicmd, "%s -n %d -hosts %s -launcher ssh ", */
                        /*         launcher, size, allhosts); */
		  sprintf(mpicmd,
			  "%s -n %d --nodelist=%s ",
			  launcher, size, allhosts);
		}
		strcat(mpicmd, launcher_options);
		strcat(mpicmd, " ");

		char* map;
		info_get_map(info, &map);
		if (map != NULL)
		{
		  strcat(mpicmd, "-N 1 --cpu-bind=verbose,map_cpu:");
		  strcat(mpicmd, map);
		  strcat(mpicmd, " ");
		  free(map);
		}

		strcat(mpicmd, timeout_string);
		if (envs != NULL)
			strcat(mpicmd, envs);
		// printf("envs: '%s'\n", envs);

		strcat(mpicmd, cmd);
		strcat(mpicmd, " ");

		// concatenate the arguments
		if(argv)
			for(i=0; argv[i] != NULL; i++) {
				strcat(mpicmd, argv[i]);
				strcat(mpicmd, " ");
			}

		// concatenate the redirection
		strcat(mpicmd, redirect);

		printf("mpicmd: %s\n", mpicmd); fflush(stdout);

		// calls the system command
		*exit_code = system(mpicmd);
		*exit_code = WEXITSTATUS(*exit_code);
		if (*exit_code == 127)
			printf("MPIX_Comm_launch(): command exited with code 127.\n"
					"Check that your desired program is in PATH.\n"
					"command was: %s\n", mpicmd);

		// unset TURBINE_LAUNCH_OPTIONS (in case turbine is the launcher)
		unsetenv("TURBINE_LAUNCH_OPTIONS");

		if (old_pwd != NULL) {
			chdir_checked(comm, old_pwd);
			free(old_pwd);
			old_pwd = NULL;
		}
		if (envs != NULL) free(envs);
		free(mpicmd);
		free(launcher);
		free(redirect);
	}

	// broadcast the exit status
	MPI_Bcast(exit_code, 1, MPI_INT, root, comm);

fn_exit:
	if(allhosts) free(allhosts);
	return r;
fn_error:
	MPI_Abort(MPI_COMM_WORLD,-1);
	goto fn_exit;

	// Unreachable - for Eclipse:
	return -1;
}

static inline void
chdir_checked(MPI_Comm comm, const char* d) {
	int rc = chdir(d);
	if (rc != 0) {
		printf("MPIX_Comm_launch(): info key chdir=%s\n", d);
		printf("                    Could not change directories!\n");
		perror("MPIX_Comm_launch: ");
		MPI_Abort(comm, 1);
	}
}

// Local Variables:
// indent-tabs-mode: t;
// End:
