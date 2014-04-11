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
#define natoms 89
#define PI 3.14159265 
using namespace std;


int core_repulsion(double x[], double y[], double z[])  //takes care of repulsive interactions when any two residues come
    {                                                    //too close by undoing the move
    int i, bond;
    double d ;
    bond = 0;
    for (int j = 1; j <= natoms ; j++){
        for (int k = j+1; k <= natoms ; k++){
        d = DIS(x[j],x[k],y[j],y[k],z[j],z[k]);
        if (d < 4.0) 
        bond++;
        //cout << d << "    "<<bond<<endl;    
                                            }
                                       }
    //cout << bond <<endl;
    return bond;
    }

int junct_linkage(double x[], double y[], double z[]) // puts distance restraint between the adjacent residues in a junction
    {
    double d_bond, d1[20] ;
    int bond = 0 ;
    int junct[] = {20,21,22,23,55,56,57,64,65} ;
    for (unsigned int i = 0 ; i < (sizeof(junct)/sizeof(int)-1) ; i++){
        int j1 = junct[i], j2 = junct[i+1] ;
        d_bond = DIS(x[j1],x[j2],y[j1],y[j2],z[j1],z[j2]) ;
        d1[i] = d_bond ;
        //cout << d1[i] <<"    "<<i<<"    "<<j1<<"    "<<j2<<endl;
                                                             }
    if (d1[0] > 6.6 or d1[1] > 7.2 or d1[2] > 7.45 or d1[3] > 11.0  or d1[4] > 7.30 or d1[5] > 5.3 or d1[7] > 5.3 ) bond++ ; 
    //cout << bond << endl;
    return bond ;
    }

/*int bond_segment(double x[], double y[], double z[])  // puts distacne restraint between adjacent residues in a duplex, loop  
    {                                                        
    int duplex_1a[] = {1,2,3,4,5}, duplex_1b[] = {89,88,87,86,85,84} ;
    int duplex_2a[] = {8,9,10,11,12,13,14,15,16,17,18,19,20},   duplex_2b[] = {78,77,76,75,74,73,72,71,70,69,67,66,65} ;
    int duplex_3a[] = {23,24,25,26,27,28,29,30,31,32,33,34}, duplex_3b[] = {55,54,53,52,51,50,49,48,47,46,45,44} ;
    int duplex_4a[] = {57,58}, duplex_4b[] = {64,63} ;  
    int int_loop1[] = {5,6,7,8}, int_loop2[] = {78,79,80,81,82,83,84} ;
    int loop1[] ={34,35,36,37,38,39,40,41,42,43,44}, loop2[] = {58,59,60,61,62,63} ;
    int bond = 0 ; 
    double d1_bond, d2_bond ;
    int j1,j2 ;
    //let us now calculate the bond and bridge distance for various duplexes,and bond distance for the two loops
    for (int i = 0 ; i < (sizeof(duplex_1a)/sizeof(int)-1) ; i++){
        j1 = duplex_1a[i], j2 = duplex_1b[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        d2_bond = DIS(x[j2],x[j2-1],y[j2],y[j2-1],z[j2],z[j2-1]) ;
        //if (d1_bond < 4.5 or d1_bond > 5.5 or d2_bond < 4.5 or d2_bond > 5.5) bond ++ ;
        cout << bond<<"    "<<d1_bond <<"    "<< d2_bond << endl;
                                                                 }
    for (int i = 0 ; i < (sizeof(duplex_2a)/sizeof(int)-1) ; i++){
        j1 = duplex_2a[i], j2 = duplex_2b[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        d2_bond = DIS(x[j2],x[j2-1],y[j2],y[j2-1],z[j2],z[j2-1]) ;
        if (d1_bond < 4.3 or d1_bond > 5.5 or d2_bond < 4.3 or d2_bond > 5.5) bond ++ ;
        //cout << bond<<"    "<<d1_bond <<"    "<< d2_bond << endl;
                                                                 }
    for (int i = 0 ; i < (sizeof(duplex_3a)/sizeof(int)-1) ; i++){
        j1 = duplex_3a[i], j2 = duplex_3b[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        d2_bond = DIS(x[j2],x[j2-1],y[j2],y[j2-1],z[j2],z[j2-1]) ;
        if (d1_bond < 4.1 or d1_bond > 5.5 or d2_bond < 4.1 or d2_bond > 5.5) bond ++ ;
        //cout << bond<<"    "<<d1_bond <<"    "<< d2_bond << endl;
                                                                 }
    for (int i = 0 ; i < (sizeof(duplex_4a)/sizeof(int)-1) ; i++){
        j1 = duplex_4a[i], j2 = duplex_4b[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        d2_bond = DIS(x[j2],x[j2-1],y[j2],y[j2-1],z[j2],z[j2-1]) ;
        if (d1_bond < 4.5 or d1_bond > 5.5 or d2_bond < 4.5 or d2_bond > 5.5) bond ++ ;
        //cout << bond<<"    "<<d1_bond <<"    "<< d2_bond << endl;
                                                                 }
    for (int i = 0 ; i < (sizeof(loop1)/sizeof(int)-1) ; i++){
        j1 = loop1[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        if (d1_bond < 4.3 or d1_bond > 8.5 ) bond ++ ;
        //cout << bond<<"    "<<d1_bond  << endl;
                                                                 }
    for (int i = 0 ; i < (sizeof(loop2)/sizeof(int)-1) ; i++){
        j1 = loop2[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        if (d1_bond < 4.2 or d1_bond > 6.3 ) bond ++ ;
        //cout << bond<<"    "<<d1_bond  << endl;
                                                                 }
    for (int i = 0 ; i < (sizeof(loop3)/sizeof(int)-1) ; i++){
        j1 = loop3[i] ;
        d1_bond = DIS(x[j1],x[j1+1],y[j1],y[j1+1],z[j1],z[j1+1]) ;
        if (d1_bond < 4.4 or d1_bond > 7.4 ) bond ++ ;
        //cout << bond<<"    "<<d1_bond  << endl;
                                                                 }
    return bond ;
    } */

/*int bridge_duplex(double x[], double y[], double z[]) //puts distance restraint between the globs across the duplex 
    {
    int duplex_1a[] = {1,2,3,4,5,6,7}, duplex_1b[] = {72,71,70,69,68,67,66} ;
    int duplex_2a[] = {10,11,12,13},   duplex_2b[] = {25,24,23,22} ;
    int duplex_3a[] = {27,28,29,30,31,32}, duplex_3b[] = {43,42,41,40,39,38} ;
    int duplex_4a[] = {49,50,51,52,53}, duplex_4b[] = {65,64,63,62,61} ;  
    int bond = 0 ; 
    double d_bridge ;
    int j1,j2 ;
    //let us now calculate bridge distance for various duplexes
    for (int i = 0 ; i < sizeof(duplex_1a)/sizeof(int) ; i++){
        j1 = duplex_1a[i], j2 = duplex_1b[i] ;
        d_bridge = DIS(x[j1],x[j2],y[j1],y[j2],z[j1],z[j2]) ;
        if (d_bridge < 11.0 or d_bridge > 11.6 ) bond ++ ;
        //cout << bond<<"    "<<d_bridge  << endl;
                                                                 }
    for (int i = 0 ; i < sizeof(duplex_2a)/sizeof(int) ; i++){
        j1 = duplex_2a[i], j2 = duplex_2b[i] ;
        d_bridge = DIS(x[j1],x[j2],y[j1],y[j2],z[j1],z[j2]) ;
        if (d_bridge < 10.7 or d_bridge > 12.0 ) bond ++ ;
        //cout << bond<<"    "<<d_bridge  << endl;
                                                                 }
    for (int i = 0 ; i < sizeof(duplex_3a)/sizeof(int) ; i++){
        j1 = duplex_3a[i], j2 = duplex_3b[i] ;
        d_bridge = DIS(x[j1],x[j2],y[j1],y[j2],z[j1],z[j2]) ;
        if (d_bridge < 11.0 or d_bridge > 11.9 ) bond ++ ;
        //cout << bond<<"    "<<d_bridge  << endl;
                                                                 }
    for (int i = 0 ; i < sizeof(duplex_4a)/sizeof(int) ; i++){
        j1 = duplex_4a[i], j2 = duplex_4b[i] ;
        d_bridge = DIS(x[j1],x[j2],y[j1],y[j2],z[j1],z[j2]) ;
        if (d_bridge < 10.9 or d_bridge > 11.6 ) bond ++ ;
        //cout << bond<<"    "<<d_bridge  << endl;
                                                                 }
    return bond ;
    }*/
    


int pseudoknot(double x[], double y[], double z[])
    {
    double d1,d2,d3,d4,d5 ;
    int bond ;
    bond = 0 ;
    d1 = DIS(x[37],x[83],y[37],y[83],z[37],z[83]) ;
    d2 = DIS(x[38],x[82],y[38],y[82],z[38],z[82]) ;
    d3 = DIS(x[39],x[81],y[39],y[81],z[39],z[81]) ;
    d4 = DIS(x[40],x[80],y[40],y[80],z[40],z[80]) ;
    d5 = DIS(x[41],x[79],y[41],y[79],z[41],z[79]) ;
    //cout << d1 <<"  "<<d2<<"  "<<d3<<"  "<<d4<<"  "<<d5<< endl;
    if (d1 > 74.5 or d2 > 72.5 or d3 > 70.6 or d4 > 69.5 or d5 > 69.70) bond++ ;     //constrain the initial distance
    if (d1 < 11.0 or d2 < 11.0 or d3 < 11.0 or d4 < 11.0 or d5 < 11.0) bond++ ;     //check the base pairing distance
    //cout << bond << endl;
    return bond ;
    }


int pseudo_score(double x[], double y[], double z[])
{ 
    int seg1[] = {37,38,39,40,41}, len_seg1 = sizeof(seg1)/sizeof(int) ;
    int seg2[] = {83,82,81,80,79}, len_seg2 = sizeof(seg2)/sizeof(int) ;
    double d1 ;
    int count1=0 ;
    int count = 0 ;
    for (int i = 0 ; i <(len_seg1-1) ; i++){
        int j1 = seg1[i], j2=seg2[i] ;
        d1 = DIS(x[j1],x[j2],y[j1],y[j2],z[j1],z[j2]) ;
        //cout << d1 << endl;
        for (int k = 1 ; k < 1000 ; k++ ){
            double t = (double)k/10 ;
            if (d1 > 11.0 and d1 <= 11.5) {
                count1 = 0 ;
                //cout << "break one " <<count1<< endl; 
                break ;                                }
            else if (d1 > (11.5) and d1 <= (12.0)) {
                count1 = 1 ; 
                //cout << "break two  " <<count1 << endl;
                break ;                                     } 

            else if (d1 > 12.0 and d1 < (12.0+t)) {
                count1 = 1+k ; 
                //cout << "break 3   "<<d1<<"    "<<(5.16-0.84-t)<<"    "<<(5.16+0.84+t)<<"    "<<count1<< endl;
                break ; 
                                                                }                                  
        }
        count = count + count1 ;
        //cout << count << endl;
    }

    return count ;
} 



















                                                                 
