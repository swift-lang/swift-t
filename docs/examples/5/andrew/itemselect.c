#include <stdio.h>
#include <stdlib.h>
#include "itemselect.h"

int selnum(FILE* f1, FILE* f2, int n);

int main(int argc, char** argv){
	
    int num=atoi(argv[1]);

    FILE *f1, *f2;

    f1 = fopen("file1.txt", "r");
    f2 = fopen("file2.txt", "r");

    printf("The resulting number is %d\n", selnum(f1, f2, num));
    fclose(f1);
    fclose(f2);

    return 0;
}

int selnum(FILE* f1, FILE* f2, int n){

    /* go to nth item in file f2 and call it m */
    size_t size;
    char *read_str1, *read_str2;
    int m;
    
    size=100;

    read_str1 = (char *) malloc(size+1);
    read_str2 = (char *) malloc(size+1);

    fseek(f2, n, SEEK_SET);
    getline(&read_str1, &size, f2);
    m = atoi(read_str1);

    /* go to mth item in file f1 */
    fseek(f1, m, SEEK_SET);
    getline(&read_str2, &size, f1);

    /* return mth item in file f1 */
    return (atoi(read_str2));
}
