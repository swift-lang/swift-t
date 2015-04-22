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
 * Functions to manage services running alongside Turbine
 *
 * Extracted from worker code: Jun 27 2014
 *
 * Authors: wozniak, armstrong
 */

#include "src/turbine/services.h"

#include <signal.h>
#include <stdio.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/wait.h>

#include <list.h>
#include <log.h>
#include <tools.h>

#define service_log(format, args...) \
  log_printf("TURBINE_WORKER_SERVICE: " format, ## args)

/** Did we launch a service process? */
static bool service_launched;
/** The PID of the service process */
static pid_t service_pid;

static void service_launch(char* cmd);
static bool service_shutdown(int* status);

turbine_code
turbine_service_init()
{
  char* cmd = getenv("TURBINE_WORKER_SERVICE");
  if (cmd == NULL) return TURBINE_SUCCESS;

  service_pid = fork();
  if (service_pid != 0)
    service_log("pid: %i", service_pid);
  else
    service_launch(cmd);

  service_launched = true;

  return TURBINE_SUCCESS;
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
  {
    service_log("ERROR: could not launch TURBINE_WORKER_SERVICE: %s\n",
           cmd);
    exit(1);
  }
}

void
turbine_service_finalize()
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

  service_log("WARNING: Turbine worker service did not exit!");

  return false;
}
