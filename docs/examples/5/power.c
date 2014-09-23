/*
Powerseries of the form sum=Tn + Tn-1 + Tn-2 + ..., where Tk=Tk-1*(x/n) for a fixed large n and user supplied x.
*/

#include <stdio.h>
#include <stdlib.h>
#define ACCURACY 0.0001

int main(int argc, char** argv) {

    if (argc != 2) {
        puts("requires 1 argument!");
        exit(1);
    }

    float x = atof(argv[1]);

    int   count;
    float term, sum;
    term = sum = count = 1;

    for (int n = 1; n <= 100; n++){
        term = term * x/n;
        sum = sum + term;
        count = count + 1;
        if (term < ACCURACY)
            break;
    }

    printf("Terms = %d \n Sum = %f\n", count, sum);

    return 0;
}

// Local Variables:
// c-basic-offset: 4
// End:
