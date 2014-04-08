/*
Powerseries of the form sum=Tn + Tn-1 + Tn-2 + ..., where Tk=Tk-1*(x/n) for a fixed large n and user supplied x.
*/
#include <stdio.h>
#include <stdlib.h>
#include "ex2.h"
#define ACCURACY 0.0001

int power_main(int argc, char** argv){
    int n, count;
    float x, term, sum;

    x=atof(argv[1]);

    term = sum = count = 1;

    for (n=1; n <= 100; n++){
        term = term * x/n;
        sum = sum + term;
        count = count + 1;
        if (term < ACCURACY)
            break;
    }

    printf("Terms = %d \n Sum = %f\n", count, sum);

    return 0;
}
