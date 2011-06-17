
#include "src/turbine/turbine.h"

/**
   Maximum number of tokens in a turbine command line
 */
#define TURBINE_COMMAND_MAX_TOKENS

/**
   Maximum number of characters in a turbine command token
 */
#define TURBINE_COMMAND_MAX_TOKEN

turbine_code turbine_command(char* cmd);
