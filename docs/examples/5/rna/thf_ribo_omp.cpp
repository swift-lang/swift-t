#include <iostream>
#include <fstream>
#include <iomanip>
#include <cstdio>
#include <cmath>
#include <unistd.h>    // for the chdir command
#include <cstdlib>     // required for including rand and srand
#include <ctime>
#include "thf_ribo_restraint.h"
#include "thf_ribo_moves.h"
#include <omp.h>
#define TFACTR 0.9
#define DIS(a,b,c,d,e,f) sqrt(((b)-(a))*((b)-(a)) + ((d)-(c))*((d)-(c)) + ((f)-(e))*((f)-(e)))
#define natoms 89
#define N 3  //total number of segments
#define M 3  //number of loop residues
#define PI 3.14159265 
using namespace std;

double calc_intensity(int i,double x[], double y[], double z[], double q_val[], double scat_int_ref[], double glob_ff[]);
void trans_n_rot(int seed1, double x[], double y[], double z[],double x_old[], double y_old[], double z_old[]);
int metrop(int seed, double chi_diff, double t);
int core_repulsion(double x[], double y[], double z[]);
int junct_linkage(double x[], double y[], double z[]);
int bond_segment(double x[], double y[], double z[]);
int bridge_duplex(double x[], double y[], double z[]);
int pseudoknot(double x[], double y[], double z[]);
int pseudo_score(double x[], double y[], double z[]);
void write_pdb(double x[], double y[], double z[], string c2[], string c4[], int m);

int main(int argc, char** argv){
	//int iorder[100], ans, nsucc, count, loop0, repel, branch_sep, seg_restraint, loop_duplex ;
	int nsucc ;
	double x[100], y[100], z[100], x_old[100], y_old[100], z_old[100],rgyr ;
	string c1[100],c3[100],c4[100],c5[100], c12[100];
	string c10[100], c11[100],c2[100],c6[100];

	double d11, d21, d31, d41, d51, d12, d22, d32, d42, d52   ;
	double d11_par, d21_par, d31_par, d41_par, d51_par, d12_par, d22_par, d32_par, d42_par, d52_par   ;


	double q_val[210], glob_ff[210],q_ref[210], scat_int_ref[210], d, scat_int_calc, q,chi_2_old;
	ifstream infile2;
	infile2.open("glob_par.dat");
	ifstream infile3;
	infile3.open("scat_int_native.txt");
	for (unsigned int i = 0; i< 101 ; i++){
		infile2 >> q_val[i] >> glob_ff[i] ;
		infile3 >> q_ref[i] >> scat_int_ref[i];
	}
	//cout << glob_ff[5] <<"    "<<scat_int_ref[10]<<endl;
	infile2.close();
	infile3.close();
	////// m denotes the pdb file number for which l_max time p runs will be running
	//------------------------------------------------------------------------------
	double chi_old[210], chi_new[210],chi_old_total, chi_new_total, chi_diff, chi_1, chi_2 ;
	for (int m = 1; m <= 1; m++){
		double t = 0.5 ;
		int pseudo_penalty = 0 ;
		int flag = 0 ;
		ofstream out1;
		char final[50];
		sprintf(final, "result_1/xtest_%d.txt", m);
		out1.open(final);

		ifstream infile1;
		infile1.open("thf_ribo_deform_CM_new.pdb");
		//infile1.open("thf_ribo_CM_native.pdb");

		for (unsigned int i = 1; i<= natoms ; i++){
			infile1 >> c1[i] >>c2[i] >>c3[i] >>c4[i] >>c5[i] >>c6[i] >> x[i] >> y[i] >> z[i]>>c10[i]>>c11[i]>>c12[i];
			//cout << setw(10)<<c3[10] << "    "<<setw(10)<<y[11]<<"    "<<setw(10)<<z[12]<< endl;

		}
		//cout << x[1]<<"    "<<y[5]<<"    "<<z[7]<<endl;
		infile1.close();
		int l_max = 5000 ;
		for (int p = 1; p <=60 ; p++){
			nsucc = 0 ;
			for (int l = 1; l <=l_max ; l++){

				// store the old coordinates
				for (int i = 1; i <= natoms ; i++){
					x_old[i] = x[i], y_old[i] = y[i], z_old[i] = z[i] ;
				}
				//let us do the move (translation and rotation) now

				int seed1 = ((p-1)*l_max+l)*m ;
				int seed2 = ((p-1)*l_max+l)*m + m ;
				int seed3 = ((p-1)*l_max+l)*m + 2*m ;
				trans_n_rot(seed1,x,y,z,x_old,y_old,z_old);

				int chunk = 5 ;
				int tid ;
#pragma omp parallel shared(x,y,z,x_old, y_old, z_old, q_val, scat_int_ref, glob_ff,chunk) private(i,tid)
				{
					tid = omp_get_thread_num();
#pragma omp for schedule(dynamic,chunk)
					for (int i=0; i<101; i++)
					{
						chi_old[i] = calc_intensity(i,x_old, y_old, z_old, q_val, scat_int_ref, glob_ff);
						chi_new[i] = calc_intensity(i,x, y, z, q_val, scat_int_ref, glob_ff);
						cout << tid <<"    "<<chi_old[i]<<"    "<<chi_new[i] << endl;
					}

				}  /* end of parallel section */

				chi_old_total = 0;
				chi_new_total = 0;
				for (int i=0; i<101; i++){
					chi_old_total = chi_old_total + chi_old[i];
					chi_new_total = chi_new_total + chi_new[i];
				}
				chi_1 = chi_old_total/101/110000;
				chi_2 = chi_new_total/101/110000 ;
				chi_diff = chi_2 - chi_1;
				//printf("%f and %f : is the value of chi_1 and chi_2\n",chi_1,chi_2);
				//cout << chi_diff <<"    "<<t << endl;
				int vdw_check, junct_link_check,bond_segment_check,bridge_duplex_check,pseudoknot_check,ans;
				vdw_check = core_repulsion(x,y,z);
				junct_link_check = junct_linkage(x,y,z);
				//bond_segment_check = bond_segment(x,y,z);
				//bridge_duplex_check = bridge_duplex(x,y,z);
				pseudoknot_check = pseudoknot(x,y,z); //loose one, big range
				ans = metrop(seed2,chi_diff, t);
				pseudo_penalty = pseudo_score(x,y,z); //strict one and score system
				//cout << pseudo_penalty << endl;
				//cout << vdw_check <<"    "<<junct_link_check<<"  "<<pseudoknot_check<<"  "<<ans<<endl;


				if (vdw_check != 0){
					// undo the move (back to earlier coordinates)
					for (int i = 1; i <= natoms ; i++){
						x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
					}
					//cout << "repel" << endl;
				}

				else if (junct_link_check != 0){
					// undo the move (back to earlier coordinates)
					for (int i = 1; i <= natoms ; i++){
						x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
					}
					//cout << "loop0" << endl;
				}
				/*else if (bond_segment_check != 0){
				// undo the move (back to earlier coordinates)
				for (int i = 1; i <= natoms ; i++){
				x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
				}
				//cout << "loop0" << endl;
				}
				else if (bridge_duplex_check != 0){
				// undo the move (back to earlier coordinates)
				for (int i = 1; i <= natoms ; i++){
				x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
				}
				//cout << "loop0" << endl;
				}*/
				else if (pseudoknot_check != 0){
					// undo the move (back to earlier coordinates)
					for (int i = 1; i <= natoms ; i++){
						x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
					}
					//cout << "loop0" << endl;
				}
				else if (ans != 1){
					// undo the move (back to earlier coordinates)
					for (int i = 1; i <= natoms ; i++){
						x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
					}
					//cout << "ans" << endl;
				} 
				else {
					nsucc++ ;
					//out1 << nsucc <<"  "<< ((p-1)*l_max+l) <<"    "<< t << "    "<<chi_1<<"    "<<pseudo_penalty<< endl;                     
				} //end of immediate else loop

				///// this part of the code is to constrain the pseudoknot distance once it is acheived //////
				//----------------------------------------------------------------------------------------------
				if (flag == 0 and pseudo_penalty < 120){
					d11_par = DIS(x[37],x[83],y[37],y[83],z[37],z[83]) ;
					d21_par = DIS(x[38],x[82],y[38],y[82],z[38],z[82]) ;
					d31_par = DIS(x[39],x[81],y[39],y[81],z[39],z[81]) ;
					d41_par = DIS(x[40],x[80],y[40],y[80],z[40],z[80]) ;
					d51_par = DIS(x[41],x[79],y[41],y[79],z[41],z[79]) ;
					flag = 1 ;
				}
				else if (flag == 1 and pseudo_penalty >= 60){
					d11 = DIS(x[37],x[83],y[37],y[83],z[37],z[83]) ;
					d21 = DIS(x[38],x[82],y[38],y[82],z[38],z[82]) ;
					d31 = DIS(x[39],x[81],y[39],y[81],z[39],z[81]) ;
					d41 = DIS(x[40],x[80],y[40],y[80],z[40],z[80]) ;
					d51 = DIS(x[41],x[79],y[41],y[79],z[41],z[79]) ;
					if (d11 > (d11_par+1.0) or d21 > (d21_par+1.0) or d31 > (d31_par+1.0) or d41 > (d41_par+1.0) or d51 > (d51_par+1.0)){
						// undo the move (back to earlier coordinates)
						for (int i = 1; i <= natoms ; i++){
							x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
						}
						//cout << "ans" << endl;
					} }
				else if (flag == 1 and pseudo_penalty < 60){
					d12_par = DIS(x[37],x[83],y[37],y[83],z[37],z[83]) ;
					d22_par = DIS(x[38],x[82],y[38],y[82],z[38],z[82]) ;
					d32_par = DIS(x[39],x[81],y[39],y[81],z[39],z[81]) ;
					d42_par = DIS(x[40],x[80],y[40],y[80],z[40],z[80]) ;
					d52_par = DIS(x[41],x[79],y[41],y[79],z[41],z[79]) ;
					flag = 2 ;
				}
				else if (flag == 2){
					d12 = DIS(x[37],x[83],y[37],y[83],z[37],z[83]) ;
					d22 = DIS(x[38],x[82],y[38],y[82],z[38],z[82]) ;
					d32 = DIS(x[39],x[81],y[39],y[81],z[39],z[81]) ;
					d42 = DIS(x[40],x[80],y[40],y[80],z[40],z[80]) ;
					d52 = DIS(x[41],x[79],y[41],y[79],z[41],z[79]) ;
					if (d12 > (d12_par+1.0) or d22 > (d22_par+1.0) or d32 > (d32_par+1.0) or d42 > (d42_par+1.0) or d52 > (d52_par+1.0)){
						// undo the move (back to earlier coordinates)
						for (int i = 1; i <= natoms ; i++){
							x[i] = x_old[i], y[i] = y_old[i], z[i] = z_old[i] ;
						}
						//cout << "ans" << endl;
					} }
					//----------------------------------------------------------------------------------------

			} //end of loop for l
			t *=TFACTR; 
			out1 << nsucc <<"  "<< (p*l_max) <<"    "<< t << "    "<<chi_1<<"    "<<pseudo_penalty<< endl;                     
			//cout <<nsucc<<endl;     
			if (chi_1 > 15) break;
			//write_pdb(x,y,z,c2,c4,m);
			if (nsucc == 0 and pseudo_penalty < 50){
				write_pdb(x,y,z,c2,c4,m);
				break ;
			}
			if ((p*l_max) == 250000 and pseudo_penalty > 120 ) break ;
			if ((p*l_max) > 275000 and pseudo_penalty < 50 ) {
				write_pdb(x,y,z,c2,c4,m);
				break ;
			}

		} // end of loop for p

		out1.close();
	} // end of loop for m
	return 0;
}




double calc_intensity(int i,double x[], double y[], double z[], double q_val[], double scat_int_ref[], double glob_ff[])
{
	double scat_int_calc,chi,d;
	chi = 0 ;
	scat_int_calc = 0.0;
	for (int j = 1; j <= natoms ; j++){
		scat_int_calc += glob_ff[i]*glob_ff[i];
		for (int k = j+1; k <= natoms ; k++){
			d = DIS(x[j],x[k],y[j],y[k],z[j],z[k]);
			if (q_val[i] == 0.0 ) {scat_int_calc += 2*glob_ff[i]*glob_ff[i];}
			else {scat_int_calc += 2*glob_ff[i]*glob_ff[i]*sin(q_val[i]*d)/(q_val[i]*d);}
		}
	}
	chi = sqrt((scat_int_ref[i] - scat_int_calc)*(scat_int_ref[i] - scat_int_calc));
	//cout <<  scat_int_calc << endl;
	//chi_2 = chi/498/18000;
	return chi;
	//cout << chi << endl;

}


/* Metropolis test for accepting or rejecting the move  */ 

int metrop(int seed, double chi_diff, double t)
{  
	srand(seed);  // random number seed
	int ans;
	double r = ((double) rand() / (RAND_MAX)); //generates random numbers between 0 and 1.
	if (chi_diff < 0 || r < exp(-chi_diff/t)) {
		ans = 1;
		//cout << r << " " << chi_diff <<" " <<exp(-chi_diff/t)<< endl;
	}
	else { ans = 0;}
	//cout << r << " " << chi_diff <<" " <<exp(-chi_diff/t)<< endl;
	return ans;
}


void write_pdb(double x[], double y[], double z[], string c2[], string c4[], int m)
{
	ofstream out2;
	char final1[50];
	sprintf(final1, "result_2/xtest_%d.pdb", m);
	out2.open(final1);
	for (unsigned int i = 1; i<= natoms ; i++){
		out2 << "ATOM"<<"  "<<setw(5)<< c2[i] <<setw(4)<<"CA "<< setw(5) << c4[i] <<" "<<"X"<<setw(4)<<c2[i]<<      \
			fixed<<setprecision(3)<<setw(12)<<x[i]<<setprecision(3)<<setw(8)<<y[i]<<setprecision(3)<<setw(8)<<z[i]<<setw(6)<<"1.00"  \
			<<setw(6)<<"0.00"<<setw(12)<<"C"<< endl; 
	}
	out2.close();
}   



