/********************************************************************************/
/*                                                                              */
/*              DicoreConstants.java                                            */
/*                                                                              */
/*      General Constants for Dynamic Intelligent Assistive Debugger            */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2013 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2013, Brown University, Providence, RI.                            *
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



package edu.brown.cs.diad.dicore;

import edu.brown.cs.ivy.xml.IvyXmlWriter;

public interface DiadConstants
{


interface DiadCommand {
   void process(IvyXmlWriter xw) throws Exception;
   String getCommandName();
   boolean isImmediate();
}



enum DiadCandidateState {
   INITIAL,
   FINDING_SYMPTOM,
   NO_SYMPTOM_FOUND,
   DOING_ANALYSIS,
   FINDING_STARTING_FRAME,
   FINDING_ALL_LOCATIONS,
   DOING_BASE_EXECUTION,
   FINDING_EXECUTED_LOCATIONS,
   PREPARING_DATA,
   READY,
   NO_USER_STACK,
   NO_ANALYSIS,
   NO_START_FRAME,
   NO_LOCATIONS_FOUND,
   NO_BASE_EXECUTION,
   NO_FINAL_LOCATIONS,
   DEAD,
   INTERUPTED,
}



enum DiadSymptomType {
   NONE,
   EXCEPTION,
   ASSERTION,
   VARIABLE,
   EXPRESSION,
   LOCATION,
   NO_EXCEPTION,
   CAUGHT_EXCEPTION,
}

enum DiadValueOperator {
   NONE,
   EQL, NEQ, GTR, GEQ, LSS, LEQ,
}


enum DiadAnalysisState {
   NONE,
   PENDING,
   READY,
   FAIL,
}


enum DiadAnalysisFileMode {
   ALL_FILES,
   COMPUTED_FILES,
   STACK_FILES,
   FAIT_FILES,
   USER_FILES,
}


enum DiadValueKind {
   UNKNOWN, PRIMITIVE, STRING, CLASS, OBJECT, ARRAY
}


enum DiadAskType {
   GENERAL,
   EXPLAIN,
   LOCATIONS,
   REPAIRS,
   TEST1,
   TEST2,
   TEST3,
}


/********************************************************************************/
/*                                                                              */
/*      Constants for analysis                                                        */
/*                                                                              */
/********************************************************************************/

double DEFAULT_PRIORITY = 0.5;



/********************************************************************************/
/*                                                                              */
/*      Constants for execution                                                          */
/*                                                                              */
/********************************************************************************/

int MAX_SEEDE_STEPS = 1000000;
int MAX_SEEDE_DEPTH = 100;


}       // end of interface DicoreConstants




/* end of DicoreConstants.java */

