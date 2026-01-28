/********************************************************************************/
/*                                                                              */
/*              DiexecuteConstants.java                                         */
/*                                                                              */
/*      Constants for SEEDE (execution) interface                               */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 *  Permission to use, copy, modify, and distribute this software and its        *
 *  documentation for any purpose other than its incorporation into a            *
 *  commercial product is hereby granted without fee, provided that the          *
 *  above copyright notice appear in all copies and that both that               *
 *  copyright notice and this permission notice appear in supporting             *
 *  documentation, and that the name of Brown University not be used in          *
 *  advertising or publicity pertaining to distribution of the software          *
 *  without specific, written prior permission.                                  *
 *                                                                               *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS                *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND            *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY      *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY          *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,              *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS               *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE          *
 *  OF THIS SOFTWARE.                                                            *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.diad.diexecute;

import edu.brown.cs.diad.dicore.DiadConstants;

public interface DiexecuteConstants extends DiadConstants
{


enum DiexecuteVariableType {
   PARAMETER,
   FIELD,
   THIS_FIELD,
}

interface DiexecuteChangeVariable {
   DiexecuteVariableType getVariableType();
   String getName();
}

/********************************************************************************/
/*                                                                              */
/*      Constants for scoring patches -- not used yet                           */
/*                                                                              */
/********************************************************************************/

double DEFAULT_SCORE = 0.25;

int MAX_CHECKED_OK = 100;
int MIN_CHECKED_OK = 60;
long MAX_SEEDE_OK = 600000;
long MIN_SEEDE_OK = 50000;
int MAX_CHECKED = 350;
long MAX_SEEDE_TOTAL = 10000000;
double GOOD_SCORE = 0.7;



}       // end of interface DiexecuteConstants




/* end of DiexecuteConstants.java */

