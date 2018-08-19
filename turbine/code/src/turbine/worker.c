/*
 * Copyright 2014 University of Chicago and Argonne National Laboratory
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
 * worker.c
 *
 *  Created on: Aug 16, 2013
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <tools.h>
#include <adlb.h>
#include <tcl.h>

#include "src/util/debug.h"
#include "src/turbine/turbine.h"
#include "src/turbine/turbine-checks.h"
#include "src/turbine/services.h"

static void task_error(Tcl_Interp* interp, int tcl_rc, char* command);

/** Limit for biggest task ADLB_Get() can give us */
static const int MAX_TASK = 1*1000*1000*1000;

// char*  turbine_worker_command = NULL;
// Command is args[0]
char** turbine_worker_args = NULL;
char*  turbine_worker_stdin_file = NULL;
char*  turbine_worker_stdout_file = NULL;
char*  turbine_worker_stderr_file = NULL;

bool exec_worker_command(void);

#if 0
    pid_t child = fork();
    if (child == 0)
    {
      printf("child\n");
      char* cmd = "echo";
      char* cmd_argv[3];
      cmd_argv[0] = "echo";
      cmd_argv[1] = "HELLO FORK";
      cmd_argv[2] = NULL;
      execvp(cmd_argv[0], cmd_argv);
      printf("Error exec()ing command: %s\n", strerror(errno));
      exit(1);
    }
    else
    {
      printf("parent: %i\n", child);
      int status;
      int rc = waitpid(child, &status, 0);
      assert(rc > 0);
      if (WIFEXITED(status))
      {
        int exit_status = WEXITSTATUS(status);
        if (exit_status != 0)
          printf("FAIL: NONZERO EXIT: %i\n", exit_status);
      }
      if (WIFSIGNALED(status))
        printf("FAIL: SIGNAL: %i\n", WTERMSIG(status));
    }

    printf("did system code=%i.\n", ec); fflush(NULL);
#endif

/*
  Main worker loop
  TODO: priority isn't inherited from parent tasks
 */
turbine_code
turbine_worker_loop(Tcl_Interp* interp,
                    void* buffer, int buffer_size,
                    int work_type)
{
  int rc;

  turbine_code tc = turbine_service_init();
  turbine_check(tc);

  while (true)
  {
    printf("\nTOP\n");

    // These are overwritten by ADLB_Get():
    void* payload = buffer;
    int task_size = buffer_size;

    MPI_Comm task_comm;
    int answer_rank, type_recved;

    if (ADLB_Status() == ADLB_STATUS_SHUTDOWN)
      break;

    adlb_code code = ADLB_Get(work_type, &payload, &task_size, MAX_TASK,
                              &answer_rank, &type_recved, &task_comm);
    if (code == ADLB_SHUTDOWN)
      break;
    if (code != ADLB_SUCCESS)
    {
      printf("Get failed with code %i\n", code);
      return TURBINE_ERROR_ADLB;
    }
    assert(type_recved == work_type);

    // Set task communicator for parallel tasks
    turbine_task_comm = task_comm;

    char* command = payload;
    DEBUG_TURBINE("eval: %s", command);
    printf("eval: %s\n", command);
    rc = Tcl_EvalEx(interp, command, task_size-1, 0);
    if (rc != TCL_OK)
    {
      task_error(interp, rc, command);
      return TURBINE_ERROR_EXTERNAL;
    }

    if (turbine_worker_args != NULL)
      exec_worker_command();

    if (payload != buffer)
      // Free the oversized buffer created by ADLB_Get()
      free(payload);
  }

  printf("broke\n");

  turbine_service_finalize();

  return TURBINE_SUCCESS;
}

static void setup_redirects(void);

static inline void turbine_worker_free_cmd(void);

bool
exec_worker_command()
{
  pid_t child = fork();
  if (child == 0)
  {
    printf("child\n");
    /* char* cmd = turbine_worker_args[0]; */
    /* printf("command: %s\n", turbine_worker_command); */
    /* char* cmd_argv[3]; */
    /* cmd_argv[0] = turbine_worker_command; */
    /* cmd_argv[1] = "HELLO FORK"; */
    /* cmd_argv[2] = NULL; */
    // execvp(cmd, cmd_argv);
    setup_redirects();

    printf("fork: %s\n", turbine_worker_args[0]);
    execvp(turbine_worker_args[0], turbine_worker_args);

    printf("Error exec()ing command: %s\n", strerror(errno));
    return false;
  }
  else
  {
    printf("parent: %i\n", child);
    int status;
    int rc = waitpid(child, &status, 0);
    assert(rc > 0);
    if (WIFEXITED(status))
    {
      int exit_status = WEXITSTATUS(status);
      if (exit_status != 0)
        printf("FAIL: NONZERO EXIT: %i\n", exit_status);
    }
    if (WIFSIGNALED(status))
      printf("FAIL: SIGNAL: %i\n", WTERMSIG(status));
  }

  printf("CHILD OK\n"); fflush(NULL);
  turbine_worker_free_cmd();
  return true;
}

static void redirect_error_exit(const char *file, const char *purpose);
static void dup2_error_exit(const char *purpose);
static void close_error_exit(const char *purpose);

static void
setup_redirects()
{
  int rc;
  if (turbine_worker_stdin_file[0] != '\0')
  {
    int in_fd = open(turbine_worker_stdin_file, O_RDONLY);
    if (in_fd == -1)
      redirect_error_exit(turbine_worker_stdin_file,
                          "input redirection");

    rc = dup2(in_fd, 0);
    if (rc == -1) dup2_error_exit("input redirection");

    rc = close(in_fd);
    if (rc == -1) close_error_exit("input redirection");
  }

  if (turbine_worker_stdout_file[0] != '\0')
  {
    int out_fd = open(turbine_worker_stdout_file,
                      O_WRONLY | O_TRUNC | O_CREAT, 0666);
    if (out_fd == -1)
      redirect_error_exit(turbine_worker_stdout_file,
                          "output redirection");

    rc = dup2(out_fd, 1);
    if (rc == -1) dup2_error_exit("output redirection");

    rc = close(out_fd);
    if (rc == -1) close_error_exit("output redirection");
  }

  if (turbine_worker_stderr_file[0] != '\0')
  {
    int err_fd = open(turbine_worker_stderr_file,
                      O_WRONLY | O_TRUNC | O_CREAT, 0666);
    if (err_fd == -1) redirect_error_exit(turbine_worker_stderr_file,
                                          "output redirection");

    rc = dup2(err_fd, 2);
    if (rc == -1) dup2_error_exit("output redirection");

    rc = close(err_fd);
    if (rc == -1) close_error_exit("output redirection");
  }
}

static void
redirect_error_exit(const char *file, const char *purpose)
{
  fprintf(stderr, "error opening %s for %s: %s\n", file, purpose,
          strerror(errno));
  exit(1);
}

static void
dup2_error_exit(const char *purpose)
{
  fprintf(stderr, "error duplicating file for %s: %s\n", purpose,
          strerror(errno));
  exit(1);
}

static void
close_error_exit(const char *purpose)
{
  fprintf(stderr, "error closing file for %s: %s\n", purpose,
          strerror(errno));
  exit(1);
}

static inline void
turbine_worker_free_cmd()
{
  int i = 0;
  while (true)
    if (turbine_worker_args[i] == NULL)
      free(turbine_worker_args[i]);
    else
      break;
  null(&turbine_worker_args);
  free(turbine_worker_stdin_file);
  free(turbine_worker_stdout_file);
  free(turbine_worker_stderr_file);
}

static void
task_error(Tcl_Interp* interp, int tcl_rc, char* command)
{
  if (tcl_rc != TCL_ERROR)
    printf("WARNING: Unexpected return code from task: %d", tcl_rc);
  // Pass error to calling script
  const char* prefix = "Turbine worker task error in: ";
  char* msg;
  int rc = asprintf(&msg, "\n%s%s", prefix, command);
  assert(rc != -1);
  // printf("%s\n", msg);
  Tcl_AddErrorInfo(interp, msg);
  free(msg);
}
