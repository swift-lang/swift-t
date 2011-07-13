/*
 * data.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

#include "src/util/ltable.h"

#include "src/turbine/turbine.h"

int
main()
{
  turbine_code code;
  code = turbine_init();

  // Data definitions
  turbine_datum_id dA = 1, dB = 2, dC = 3, dD = 4;
  code = turbine_datum_file_create(dA, "test/data/A.txt");
  code = turbine_datum_file_create(dB, "test/data/B.txt");
  code = turbine_datum_file_create(dC, "test/data/C.txt");
  code = turbine_datum_file_create(dD, "test/data/D.txt");

  // Task dependency definitions
  /*
   Reference for turbine_transform
  typedef struct
  {
    char* name;
    char* executor;
    int inputs;
    turbine_datum_id* input;
    int outputs;
    turbine_datum_id* output;
  } turbine_transform;
  */

  turbine_transform_id tA = 1, tB = 2, tC = 3, tD = 4;

  // Task A
  turbine_transform transformA;
  transformA.name = "A";
  transformA.executor = "touch test/data/A.txt";
  turbine_datum_id outputA[1];
  transformA.inputs  = 0;
  transformA.outputs = 1;
  transformA.input  = NULL;
  transformA.output = outputA;
  transformA.output[0] = dA;
  turbine_rule_add(tA, &transformA);

  // Task B
  turbine_transform transformB;
  transformB.name = "B";
  transformB.executor = "cp test/data/A.txt test/data/B.txt";
  turbine_datum_id inputB[1];
  turbine_datum_id outputB[1];
  transformB.inputs  = 1;
  transformB.outputs = 1;
  transformB.input  = inputB;
  transformB.output = outputB;
  transformB.input[0]  = dA;
  transformB.output[0] = dB;
  turbine_rule_add(tB, &transformB);

  // Task C
  turbine_transform transformC;
  transformC.name = "C";
  transformC.executor = "cp test/data/A.txt test/data/C.txt";
  turbine_datum_id inputC[1];
  turbine_datum_id outputC[1];
  transformC.inputs  = 1;
  transformC.outputs = 1;
  transformC.input  = inputC;
  transformC.output = outputC;
  transformC.input[0]  = dA;
  transformC.output[0] = dC;
  turbine_rule_add(tC, &transformC);

  // Task D
  turbine_transform transformD;
  transformD.name = "D";
  transformD.executor =
    "cat test/data/B.txt test/data/C.txt > test/data/D.txt";
  turbine_datum_id inputD[2];
  turbine_datum_id outputD[1];
  transformD.inputs  = 2;
  transformD.outputs = 1;
  transformD.input  = inputD;
  transformD.output = outputD;
  transformD.input[0]  = dB;
  transformD.input[1]  = dC;
  transformD.output[0] = dD;
  turbine_rule_add(tD, &transformD);

  turbine_transform_id tr_id[8];
  int ready;

  turbine_rules_push();

  char executor[64];
  while (true)
  {
    turbine_ready(8, tr_id, &ready);
    if (ready == 0)
      break;
    for (int i = 0; i < ready; i++)
    {
      printf("ready: %li\n", tr_id[i]);
      turbine_code code = turbine_executor(tr_id[i], executor);
      turbine_check_verbose(code);
      printf("run: %s\n", executor);
      int result = system(executor);
      if (result)
      {
        printf("command failed with exit code: %i\n\t%s\n",
               result, executor);
        exit(result);
      }
      code = turbine_complete(tr_id[i]);
      turbine_check_verbose(code);
    }
  }

  turbine_finalize();
  puts("OK");
}
