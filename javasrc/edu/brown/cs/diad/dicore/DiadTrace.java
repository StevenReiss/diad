/********************************************************************************/
/*                                                                              */
/*              DiadTrace.java                                                  */
/*                                                                              */
/*      Representation of an execution trace                                    */
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



package edu.brown.cs.diad.dicore;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface DiadTrace extends DiadConstants
{


long getSymptomTime();
DiadTraceCall getSymptomContext();
DiadTraceCall getRootContext();
DiadTraceVarVal getException();
DiadTraceVarVal getReturnValue();
Map<String,DiadTraceVarVal> getGlobalVariables();
String getSessionId();


interface DiadTraceCall {
   File getFile();
   String getMethod();
   long getStartTime();
   long getEndTime();
   int getContextId();
   String getCallId();
   List<DiadTraceCall> getInnerTraceCalls();
   DiadTraceVarVal getLineNumbers();
   Map<String,DiadTraceVarVal> getTraceVariables();
   DiadTraceCall getParentCall();
}


interface DiadTraceVarVal {
   String getName();
   String getFullName();
   boolean hasChildren(long when);
   Collection<String> getChildNames(long when);
   DiadTraceVarVal getChild(String name,long when);
   String getDataType(long when);
   List<Long> getTimeChanges();
   long getUpdateTime(long when);
   boolean isNull(long when);
   long getStartTime();
   int getLineValue(long when);
   Long getNumericValue(long when);
   String getStringValue(long when);
   String getId(long when);
   int getArrayLength(long when);
   List<Integer> getLineNumbers();
   DiadTraceVarVal getValueAt(DiadTrace trace,long when);
}


}       // end of interface DiadTrace




/* end of DiadTrace.java */

