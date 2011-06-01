/*
 * data.c
 *
 *  Created on: May 4, 2011
 *      Author: wozniak
 */

#include <assert.h>
#include <stdio.h>

#include <ltable.h>

#include "src/turbine/turbine.h"

int
main()
{
  turbine_code code;
  code = turbine_init();

  // Data definitions
  turbine_datum_id dA = 1, dB = 2, dC = 3, dD = 4;
  code = turbine_datum_file_create(dA, "A.txt");
  code = turbine_datum_file_create(dB, "B.txt");
  code = turbine_datum_file_create(dC, "C.txt");
  code = turbine_datum_file_create(dD, "D.txt");

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

  struct ltable* tasks = ltable_create(8);

  turbine_transform_id tB = 2, tC = 3, tD = 4;

  // Task B
  turbine_transform transformB;
  transformB.name = "B";
  transformB.executor = "cp A.txt B.txt";
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
  transformC.executor = "cp A.txt C.txt";
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
  transformD.executor = "cat A.txt B.txt > D.txt";
  turbine_datum_id inputD[1];
  turbine_datum_id outputD[1];
  transformD.inputs  = 1;
  transformD.outputs = 1;
  transformD.input  = inputD;
  transformD.output = outputD;
  transformD.input[0]  = dA;
  transformD.output[0] = dD;
  turbine_rule_add(tD, &transformD);

  turbine_transform_id tr_id[8];
  int ready;
  turbine_ready(8, tr_id, &ready);
  assert(ready == 0);

  turbine_close(dA);
  while (true)
  {
    turbine_ready(8, tr_id, &ready);
    for (int i = 0; i < ready; i++)
      printf("ready: %li\n", tr_id[i]);
  }

  turbine_finalize();
}
