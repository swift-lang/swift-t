/*
 * reader.h
 *
 *  Created on: May 9, 2011
 *      Author: wozniak
 */

#ifndef READER_H
#define READER_H

int reader_init();

int reader_read(char* file);

void reader_free(int id);

void reader_finalize();

#endif
