#include <iostream>
#include <fstream>
#include <sstream>
#include <iomanip>
#include <cstdio>
#include <cstdlib>     // required for including rand and srand
#include <ctime>
#include <fstream>
#include <cmath>
#define DIS(a,b,c,d,e,f) sqrt(((b)-(a))*((b)-(a)) + ((d)-(c))*((d)-(c)) + ((f)-(e))*((f)-(e)))
#define MOD(a,b,c) sqrt(a*a+b*b+c*c)
#define PI 3.14159265 
using namespace std;

void rotate_seg(int seed1, int seg[], int len_seg, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[]);
void translate_seg(int seed1, int seg[], int len_seg, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[]);
void translate_jct(int seed1, int jct[], int len_jct, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[]);


void trans_n_rot(int seed1, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[])
    {
    srand(seed1); 
    int n1 = rand()%2+1, n2 = rand()%3+1, n3 = rand()%2+1, n4 = rand()%2+1;
    //cout << n1<<"    "<<n2<<"    "<<n3<<endl ;
    int seg1[] = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,65,66,67,68,69,70,71,72,73,74,75,76,77,78,79,80,81,82,83,84,85,86,87,88,89}; 
    int len_seg1 = sizeof(seg1)/sizeof(int);
    int seg2[] = {23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55}, len_seg2 = sizeof(seg2)/sizeof(int);
    int seg3[] = {57,58,59,60,61,62,63,64}, len_seg3 = sizeof(seg3)/sizeof(int);
    int jct1[] = {21,22}, len_jct1 = sizeof(jct1)/sizeof(int);
    int jct2[] = {56}, len_jct2 = sizeof(jct2)/sizeof(int);
    //translate_jct(seed1,jct1,len_jct1,x,y,z,x_old,y_old,z_old) ;
    if (n1 == 1) {
        if (n2 == 1) rotate_seg(seed1,seg1,len_seg1,x,y,z,x_old,y_old,z_old) ;
        else if  (n2 == 2) rotate_seg(seed1,seg2,len_seg2,x,y,z,x_old,y_old,z_old) ;
        else  rotate_seg(seed1,seg3,len_seg3,x,y,z,x_old,y_old,z_old) ;
                 }
    else {
        if (n3 == 1) {
            if (n2 == 1) translate_seg(seed1,seg1,len_seg1,x,y,z,x_old,y_old,z_old) ;
            else if  (n2 == 2) translate_seg(seed1,seg2,len_seg2,x,y,z,x_old,y_old,z_old) ;
            else translate_seg(seed1,seg3,len_seg3,x,y,z,x_old,y_old,z_old) ;
                     }
        else {
            if (n4 == 1) translate_jct(seed1,jct1,len_jct1,x,y,z,x_old,y_old,z_old) ;
            else translate_jct(seed1,jct2,len_jct2,x,y,z,x_old,y_old,z_old) ;
             }
        }  
     
    }


void rotate_seg(int seed1, int seg[], int len_seg, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[])
    {
    srand(seed1) ;
    int n1 ;
    double r1 ;
    n1 = rand()%3+1 ;
    r1 = 0.5 - ((double) rand() / (RAND_MAX)) ;
    double param, val_sin, val_cos;
    param = r1/2;
    val_sin = sin ( param * PI / 180.0 );
    val_cos = cos ( param * PI / 180.0 );
    //int index = sizeof(seg)/sizeof(int);
    //cout << index << endl;

    // this region distributes rotations, n1 = 1,2,3 --> (x,y,z) axes 
    if (n1 == 1){
        for (int i = 0 ; i < len_seg ; i++) {
        int j = seg[i], j1 = seg[0] ;
        // cout << seg[i] <<"    "<<j1<<"    "<<y[j1]<<"  "<<z[j1]<< endl;
        x[j] = x_old[j]-x_old[j1], y[j] = (y_old[j]-y_old[j1])*val_cos - (z_old[j]-z_old[j1])*val_sin, z[j] = (y_old[j]-y_old[j1])*val_sin + (z_old[j]-z_old[j1])*val_cos ; 
        
                                            }
                }
    else if (n1 == 2){
        for (int i = 0 ; i < len_seg ; i++) {
        int j = seg[i], j1 = seg[0] ;
        x[j] = (x_old[j]-x_old[j1])*val_cos + (z_old[j]-z_old[j1])*val_sin, y[j] = y_old[j]-y_old[j1], z[j] = -(x_old[j]-x_old[j1])*val_sin + (z_old[j]-z_old[j1])*val_cos ;
                                            }
                }
    else {
        for (int i = 0 ; i < len_seg ; i++) {
        int j = seg[i], j1 = seg[0] ;
        x[j] = (x_old[j]-x_old[j1])*val_cos - (y_old[j]-y_old[j1])*val_sin, y[j] = (x_old[j]-x_old[j1])*val_sin + (y_old[j]-y_old[j1])*val_cos, z[j] = z_old[j]-z_old[j1] ;
                                            }
         }

    for (int i = 0 ; i < len_seg ; i++) {
        int j = seg[i], j1 = seg[0] ;
        x[j] = x[j] + x_old[j1], y[j] = y[j] + y_old[j1], z[j] = z[j] + z_old[j1] ;
                                        
                                        }
    } 

 
void translate_seg(int seed1, int seg[], int len_seg, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[])
    {
    double r1,r2, r3 ;
    srand(seed1);  // random number seed
    //generates random numbers between 0 and +-1.
    r1 = 0.5 - ((double) rand() / (RAND_MAX)), r2 = 0.5 - ((double) rand() / (RAND_MAX)) ;
    r3 = 0.5 - ((double) rand() / (RAND_MAX)) ;
    for (int i = 0 ; i < len_seg ; i++) {
        int j = seg[i] ;
        x[j] = x_old[j] + r1 , y[j] = y_old[j] + r2 , z[j] = z_old[j] + r3 ;
                                        } 
    }

void translate_jct(int seed1, int jct[], int len_jct, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[])
    {
    srand(seed1);  // random number seed
    double r1,r2, r3,r4,r5,r6 ;
    //generates random numbers between 0 and +-1.
    r1 = 0.5 - ((double) rand() / (RAND_MAX)), r2 = 0.5 - ((double) rand() / (RAND_MAX)) ;
    r3 = 0.5 - ((double) rand() / (RAND_MAX)), r4 = 0.5 - ((double) rand() / (RAND_MAX)) ;
    r5 = 0.5 - ((double) rand() / (RAND_MAX)), r6 = 0.5 - ((double) rand() / (RAND_MAX)) ;
    if (len_jct == 1) {
        int j1 = jct[0] ;
        //cout <<j1<<" what's up    "<< endl;
        x[j1] = x_old[j1] + r1/2,  y[j1] = y_old[j1] + r2/2, z[j1] = z_old[j1] + r3/2 ;
    }
    else if (len_jct == 2) {
        int j1 = jct[0], j2 = jct[1] ;
        //cout <<j1<<"    "<<j2<< endl;
        x[j1] = x_old[j1] + r1/2,  y[j1] = y_old[j1] + r2/2, z[j1] = z_old[j1] + r3/2 ;
        x[j2] = x_old[j2] + r4/2,  y[j2] = y_old[j2] + r5/2, z[j2] = z_old[j2] + r6/2 ;
    }

    
    }
    













