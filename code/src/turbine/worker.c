
/*
 * worker.c
 *
 *  Created on: Aug 16, 2013
 *      Author: wozniak
 */

#define _GNU_SOURCE // for asprintf()
#include <signal.h>
#include <stdbool.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <adlb.h>
#include <tcl.h>

#include <list.h>
#include <log.h>
#include <tools.h>

#include "src/util/debug.h"
#include "src/turbine/turbine.h"

/** Did we launch a service process? */
static bool service_launched;
/** The PID of the service process */
static pid_t service_pid;

static void service_init(void);
static void service_finalize(void);

static void task_error(Tcl_Interp* interp, int tcl_rc, char* command);

#define service_log(format, args...) \
  log_printf("TURBINE_WORKER_SERVICE: " format, ## args)

/*
  Main worker loop
  TODO: priority isn't inherited from parent tasks
 */
turbine_code
turbine_worker_loop(Tcl_Interp* interp, void* buffer, size_t buffer_size,
                    int work_type)
{
  int rc;

  service_init();

  while (true)
  {
    MPI_Comm task_comm;
    int work_len, answer_rank, type_recved;
    adlb_code code = ADLB_Get(work_type, buffer, &work_len,
                              &answer_rank, &type_recved, &task_comm);
    if (code == ADLB_SHUTDOWN)
      break;
    turbine_task_comm = task_comm;
    if (code != ADLB_SUCCESS)
    {
      printf("Get failed with code %i\n", code);
      return TURBINE_ERROR_ADLB;
    }
    assert(work_len <= buffer_size);
    assert(type_recved == work_type);

    // Work unit is prepended with rule ID, followed by space.
    //char* rule_id_end = strchr(buffer, ' ');
    //assert(rule_id_end != NULL);
    // Set pointer to start of Tcl work unit string
    //char* command = rule_id_end + 1;

    // DEBUG_TURBINE("rule_id: %"PRId64"", atol(buffer));

    // Don't set rule_id
    char *command = buffer;
    DEBUG_TURBINE("eval: %s", command);

    // Work out length | null byte | prefix
    int cmd_len = work_len - 1 - (int)(command - (char*) buffer);
    rc = Tcl_EvalEx(interp, command, cmd_len, 0);
    if (rc != TCL_OK)
    {
      task_error(interp, rc, command);
      return TURBINE_ERROR_EXTERNAL;
    }
  }

  service_finalize();

  return TURBINE_SUCCESS;
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

static void service_launch(char* cmd);

static void
service_init()
{
  char* cmd = getenv("TURBINE_WORKER_SERVICE");
  if (cmd == NULL) return;

  service_pid = fork();
  if (service_pid != 0)
    service_log("pid: %i", service_pid);
  else
    service_launch(cmd);

  service_launched = true;

  return;
}

static void
service_launch(char* cmd)
{
  service_log("command: %s", cmd);
  struct list* words = list_split_words(cmd);

  char* args[list_size(words)+1];
  int i = 0;
  for (struct list_item* item = words->head; item;
       item = item->next, i++)
    args[i] = item->data;
  args[i] = NULL;

  list_free(words);

  int rc = execvp(args[0], args);
  if (rc == -1)
    printf("ERROR: could not launch TURBINE_WORKER_SERVICE: %s\n",
           cmd);
}

static bool service_shutdown(int* status);

static void
service_finalize()
{
  if (!service_launched) return;

  int status;
  pid_t pid = waitpid(service_pid, &status, WNOHANG);

  if (pid == 0)
  {
    bool b = service_shutdown(&status);
    if (!b) return;
  }

  if (! WIFEXITED(status))
    service_log("warning: service exited abnormally");
  service_log("service exit code: %i", WEXITSTATUS(status));

  return;
}

static bool
service_shutdown(int* status)
{
  service_log("child is running: sending SIGTERM");
  int rc = kill(service_pid, SIGTERM);
  if (rc != 0)
    service_log("warning: could not kill service");

  pid_t pid;
  int s;
  // Wait for process to exit (around 4 seconds, exponential backoff)
  double delay = 0.01;
  while (delay < 4)
  {
    pid = waitpid(service_pid, &s, WNOHANG);
    if (pid == 0)
    {
      service_log("service still running: waiting another %0.2fs", delay);
      time_delay(delay);
      delay *= 2;
    }
    else
    {
      *status = s;
      return true;
    }
  }

  printf("WARNING: Turbine worker service did not exit!");

  return false;
}
