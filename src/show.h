
/*
 * show.h
 *
 * Keystroke savers
 *
 *  Created on: Feb 9, 2015
 *      Author: wozniak
 */

#ifndef SRC_SHOW_H
#define SRC_SHOW_H

#define show_i(symbol) printf("%s: %i\n", #symbol, symbol)
#define show_ii(s1,s2) printf("%s: %i %s: %i\n", #s1, s1, #s2, s2)

#define show_p(symbol) printf("%s: %p\n", #symbol, symbol)

#define show_si(s, symbol) printf("%s: %s: %i\n", s, #symbol, symbol)
#define show_sii(s, s1,s2) printf("%s: %s: %i %s: %i\n", s, #s1, s1, #s2, s2)

#endif
