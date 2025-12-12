/********************************************************************************/
/*										*/
/*		DicontrolConstants.java 					*/
/*										*/
/*	Constants for our Dynamic Intelligent Assistive Debugger		*/
/*										*/
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 *  Permission to use, copy, modify, and distribute this software and its	 *
 *  documentation for any purpose other than its incorporation into a		 *
 *  commercial product is hereby granted without fee, provided that the 	 *
 *  above copyright notice appear in all copies and that both that		 *
 *  copyright notice and this permission notice appear in supporting		 *
 *  documentation, and that the name of Brown University not be used in 	 *
 *  advertising or publicity pertaining to distribution of the software 	 *
 *  without specific, written prior permission. 				 *
 *										 *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS		 *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND		 *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY	 *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY 	 *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,		 *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS		 *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE 	 *
 *  OF THIS SOFTWARE.								 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.diad.dicontrol;

import edu.brown.cs.diad.dicore.DiadConstants;

public interface DicontrolConstants extends DiadConstants
{

enum RunEventType { 
   NONE,
   PROCESS,
   THREAD, 
   TARGET, 
   CONSOLE 
}


enum RunEventKind {
   NONE,
   RESUME,
   SUSPEND,
   CREATE,
   TERMINATE,
   CHANGE,
   MODEL_SPECIFIC,
   HOTCODE_SUCCESS,
   HOTCODE_FAILURE,
}


enum RunThreadStateDetail {
   NONE,
   BREAKPOINT,
   CLIENT_REQUEST,
   EVALUATION,
   EVALUATION_IMPLICIT,
   STEP_END,
   STEP_INTO,
   STEP_OVER,
   STEP_RETURN,
   CONTENT
}

enum RunThreadState {
   NONE,
   NEW,
   RUNNING,
   BLOCKED,
   DEADLOCKED,
   WAITING,
   TIMED_WAITING,
   IDLE,
   STOPPED,
   EXCEPTION,
   UNKNOWN,
   DEAD,
}



enum RunThreadType {
   UNKNOWN,
   SYSTEM,
   JAVA,
   UI,
   USER,
}

}	// end of interface DicontrolConstants




/* end of DicontrolConstants.java */

