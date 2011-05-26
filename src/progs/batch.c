
#include <assert.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>

#include <readline/readline.h>
#include <readline/history.h>

#include "src/tools/reader.h"
#include "src/turbine/command.h"

char* usage(void);
void crash(char* msg);
void error(turbine_code code, reader_line line);

int
main(int argc, char* argv[])
{
  if (argc < 2)
    crash(usage());

  char* filename = argv[1];

  // Use reader to read input file

  bool result = reader_init();
  assert(result);

  long reader = reader_read(filename);
  if (reader == -1)
  {
    printf("turbine-batch: could not read: %s\n", filename);
    exit(1);
  }

  // Insert commands into turbine

  turbine_code code = turbine_init();
  if (code != TURBINE_SUCCESS)
    crash("could not initialize turbine!");

  reader_line line = reader_next(reader);
  while (line.line)
  {
    printf("line: %i cmd: %s\n", line.number, line.line);
    code = turbine_command(line.line);
    if (code != TURBINE_SUCCESS)
      error(code, line);
    line = reader_next(reader);
  }

  // Run?

  // Cleanup
  reader_free(reader);
  reader_finalize();
}

char*
usage()
{
  return "usage: batch <file>";
}

void
crash(char* msg)
{
  printf("turbine-batch: %s\n", msg);
  exit(1);
}

void
error(turbine_code code, reader_line line)
{
  char code_string[64];
  turbine_code_tostring(code_string, code);
  printf("ERROR: %s\n", code_string);
  printf("\t on line: %i\n", line.number);
  exit(1);
}
