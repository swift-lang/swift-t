//
// A very simple matrix transpose test program
//

// SKIP-THIS-TEST

import io;
import blob;
import sys;

int NRow=toint(argv("rows", "5000"));
int NCol=toint(argv("cols", "5000"));


(blob o[]) genrow ()
{
    foreach j in [1:NCol] {
    	    o[j]=blob_zeroes_float(10);
    }
}

main {
    blob M[][];

    foreach i in [1:NRow] {
        M[i]=genrow();
    }

    blob MT[][];
    foreach i in [1:NRow] {
        foreach j in [1:NCol] {
		MT[j][i]=M[i][j];
	}
    }

    wait(MT)
    {
	file f<"A.blob"> = blob_write(MT[1][1]);
    };
}
