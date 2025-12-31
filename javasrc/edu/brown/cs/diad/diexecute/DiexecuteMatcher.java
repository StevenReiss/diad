/********************************************************************************/
/*                                                                              */
/*              DiexecuteMatcher.java                                           */
/*                                                                              */
/*      Handle3 matching of an edited run versus the original run               */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.diad.diexecute;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceCall;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceValue;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVariable;
import edu.brown.cs.ivy.file.IvyLog;

class DiexecuteMatcher implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteTrace original_trace;
private DiexecuteTrace match_trace;
private DiadRepair for_repair;
private boolean test_match;                     // match a generated test
private long delta_time;

private DiexecuteCall problem_context;        // context of problem
private long problem_time;                   // time of problem in original context
private long problem_after_time;             // time of statement after problem in original 

private long control_change;                 // time of first control change
private DiexecuteCall original_change_context;     // context of first control change
private DiexecuteCall match_change_context;        // matching context of control change

private long data_change;                    // time of first data change
private DiexecuteCall original_data_context;       // context of first data change 
private DiexecuteCall match_data_context;          // matching context of first data change

private DiexecuteCall match_problem_context;       // matching context of problem change
private long match_time;                     // matching time of problem change
private long match_after_time;               // matching time at end of statement

private boolean repair_executed;                // detect if repair was executed




/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteMatcher(DiexecuteTrace orig,DiexecuteTrace match,DiadRepair repair,boolean istest)
{
   original_trace = orig;
   match_trace = match;
   for_repair = repair;
   test_match = istest;
   delta_time = 0;
   
   problem_context = (DiexecuteCall) orig.getSymptomContext();
   problem_time = orig.getSymptomTime();  
   
   problem_after_time = 0;
   if (problem_context != null) {
      DiadTraceVariable plines = problem_context.getLineNumbers();
      boolean fnd = false;
      for (DiadTraceValue vv : plines.getTraceValues(orig)) {
         long t = vv.getStartTime();
         if (t == problem_time) fnd = true;
         else if (fnd) {
            problem_after_time = t;
            break;
          }
       }
      if (fnd && problem_after_time == 0) {
         problem_after_time = problem_context.getEndTime();
       }
    }
   
   control_change = 0;
   original_change_context = null;
   match_change_context = null;
   
   data_change = 0;
   original_data_context = null;
   match_data_context = null;
   
   match_problem_context = null;
   match_time = 0;
   match_after_time = 0;
   
   repair_executed = false;
}




/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

DiadTraceCall getSymptomContext()        { return problem_context; }
long getSymptomTime()                   { return problem_time; }
long getSymptomAfterTime()              { return problem_after_time; }

DiexecuteCall getMatchSymptomContext()        { return match_problem_context; }
long getMatchSymptomTime()              { return match_time; }
long getMatchSymptomAfterTime()         { return match_after_time; }

DiexecuteCall getOriginalChangeContext()      { return original_change_context; }
DiexecuteCall getMatchChangeContext()         { return match_change_context; }
long getControlChangeTime()             { return control_change; }

DiexecuteCall getOriginalDataContext()        { return original_data_context; }
DiexecuteCall getMatchDataContext()           { return match_data_context; }
long getDataChangeTime()                { return data_change; }

boolean repairExecuted()                
{ 
   if (for_repair == null) return true;
   return repair_executed;
}




/********************************************************************************/
/*                                                                              */
/*      Compute the match                                                       */
/*                                                                              */
/********************************************************************************/

void computeMatch()
{
   DiexecuteCall origctx = original_trace.getRootContext();
   DiexecuteCall matchctx = match_trace.getRootContext();
   if (matchctx == null) return;
   if (test_match) {
      DiexecuteCall match1 = null;
      for (DiexecuteCall vc1 : matchctx.getInnerCalls()) {
         if (vc1.getMethod().equals(origctx.getMethod())) {
            match1 = vc1;
            break;
          }
       }
      if (match1 == null) return;
      delta_time = match1.getStartTime() - origctx.getStartTime();
      matchctx = match1;
    }
   
   try {
      matchContexts(origctx,matchctx);
    }
   catch (Throwable e) {
      IvyLog.logE("DIEXECUTE","Symptom matching contexts",e);
    }
   
   IvyLog.logD("DIEXECUTE","Match result " + control_change + " " +
         data_change + " " + match_time + " " + match_after_time + " " +
         repair_executed + " " + match_trace.getSessionId());
}



private void matchContexts(DiexecuteCall origctx,DiexecuteCall matchctx)
{
   if (problem_context != null && origctx.sameAs(problem_context)) { 
      match_problem_context = matchctx;
    }
   
   matchInnerContexts(origctx,matchctx);
   matchLines(origctx,matchctx);
   matchVariables(origctx,matchctx);
}



private void matchLines(DiexecuteCall origctx,DiexecuteCall matchctx) 
{
   DiexecuteVariable origline = origctx.getLineNumbers();
   DiexecuteVariable matchline = matchctx.getLineNumbers();
   File file = matchctx.getFile();
   
   int checkrepair = -1;
   if (for_repair != null && matchFiles(for_repair.getLocation().getFile(),file)) {
      checkrepair = for_repair.getLocation().getStatementLine();  
      IvyLog.logD("DIEXECUTE","CHECK REPAIR " + checkrepair + " " + 
            origctx.getContextId() + " " + matchctx.getContextId());
    }
   
   if (origline == null || matchline == null) return;
   
   long lasttime = origctx.getStartTime();
   long matchtime = matchctx.getStartTime();
   long lastmatch = matchtime;
   if (matchTime(lasttime,matchtime)) {
      Iterator<DiexecuteValue> it1 = origline.getValues(origctx.getTrace()).iterator();
      Iterator<DiexecuteValue> it2 = matchline.getValues(matchctx.getTrace()).iterator();
      boolean fnd = false;
      while (it1.hasNext() && it2.hasNext()) {
         DiexecuteValue origval = it1.next();
         DiexecuteValue matchval = it2.next();
         long thistime = origval.getStartTime();
         long trytime = matchval.getStartTime();
         long execline = origval.getNumericValue();
         long mappedline = execline;
         if (for_repair != null) {
            mappedline = for_repair.getMappedLine(file,   execline);  
          }
         if (checkrepair > 0 && execline == checkrepair) repair_executed = true;
         
         if (match_problem_context == matchctx) {
            if (lasttime <= problem_time && thistime > problem_time) {
               match_time = lastmatch;
               match_after_time = trytime;
             }
          }
         if (!fnd && 
               (!matchTime(thistime,trytime) ||
                     mappedline != matchval.getNumericValue())) {
            if (control_change <= 0 || control_change > thistime) {
               noteChange(origctx,matchctx,lasttime);
             }
            fnd = true;
          }
         lasttime = thistime;
         lastmatch = matchval.getStartTime();
       }
      if (match_problem_context == matchctx) {
         long thistime = origctx.getEndTime();
         if (lasttime <= problem_time && thistime > problem_time) {
            match_time = lastmatch;
            match_after_time = matchctx.getEndTime();
          }
       }
      if (!fnd && (it1.hasNext() || it2.hasNext())) {
         noteChange(origctx,matchctx,lasttime);
       }
    }
}



private boolean matchFiles(File f1,File f2)
{
   if (f1.equals(f2)) return true;
   try {
      f1 = f1.getCanonicalFile();
      f2 = f2.getCanonicalFile();
    }
   catch (IOException e) { }
   if (f1.equals(f2)) return true;
   return false;
}




private void matchInnerContexts(DiexecuteCall origctx,DiexecuteCall matchctx)
{
   DiffStruct diffs = computeContextDiffs(origctx,matchctx);
   Iterator<DiexecuteCall> cit1 = origctx.getInnerCalls().iterator();
   Iterator<DiexecuteCall> cit2 = matchctx.getInnerCalls().iterator();
   
   int count = 0;
   for (DiffStruct ds = diffs; ds != null; ds = ds.getNext()) {
      while (ds.getIndex() > count) {
         DiexecuteCall c1 = cit1.next();
         DiexecuteCall c2 = cit2.next();
         matchInnerContext(origctx,matchctx,c1,c2);
         ++count;
       }
      int ndel = ds.getNumDelete();
      if (ndel == 0) {
         matchInnerContext(origctx,matchctx,null,ds.getData());
       }
      else count += ndel;
    }
   while (cit1.hasNext()) {
      DiexecuteCall c1 = cit1.next();
      DiexecuteCall c2 = null;
      if (cit2.hasNext()) c2 = cit2.next();
      matchInnerContext(origctx,matchctx,c1,c2);
      ++count;
    }
}



private void matchInnerContext(DiexecuteCall origctx,DiexecuteCall matchctx,
      DiexecuteCall octx,DiexecuteCall mctx)
{
   if (octx != null && mctx != null) {
      long ostart = octx.getStartTime();
      long mstart = mctx.getStartTime();
      if (!octx.getMethod().equals(mctx.getMethod()) || !matchTime(ostart,mstart)) {
         noteChange(origctx,matchctx,Math.min(ostart,mstart-delta_time));
       }
      matchContexts(octx,mctx);
    }
   else if (octx != null) {
      long ostart = octx.getStartTime();
      noteChange(origctx,matchctx,ostart);
    }
   else if (mctx != null) {
      long mstart = mctx.getStartTime();   
      noteChange(origctx,matchctx,mstart);
    }
}



private void matchVariables(DiexecuteCall origctx,DiexecuteCall matchctx)
{
   Map<String,DiexecuteVariable> matchelts = matchctx.getVariables();
   
   for (DiexecuteVariable oval : origctx.getVariables().values()) {
      String nm = oval.getName();
      DiexecuteVariable mval = matchelts.remove(nm);
      matchVariable(origctx,matchctx,oval,mval);
    }
   for (DiexecuteVariable mval : matchelts.values()) {
      matchVariable(origctx,matchctx,null,mval);
    }
}



private void matchVariable(DiexecuteCall origctx,DiexecuteCall matchctx,
      DiexecuteVariable ovar,DiexecuteVariable mvar)
{
   List<DiexecuteValue> ovals = getVariableValues(origctx,ovar);
   List<DiexecuteValue> mvals = getVariableValues(matchctx,mvar);
   int sz = Math.max(ovals.size(),mvals.size());
   long difftime = -1;
   long lastdiff = origctx.getStartTime();
   
   // might want to match arrays and objects a bit better
   
   for (int i = 0; i < sz; ++i) {
      DiexecuteValue oval = null;
      if (i < ovals.size()) oval = ovals.get(i);
      DiexecuteValue mval = null;
      if (i < mvals.size()) mval = mvals.get(i);
      if (oval == null) {
         difftime = lastdiff;
         break;
       }
      else if (mval == null) {
         difftime = oval.getStartTime();
         if (difftime < 0) difftime = origctx.getStartTime();
         break;
       }
      else if (oval.getValue() == null) {
         if (mval.getValue() != null) {
            difftime = oval.getStartTime();
            if (difftime < 0) difftime = origctx.getStartTime();
            break;
          }
       }
      else if (!oval.getValue().equals(mval.getValue())) {
         difftime = oval.getStartTime();
         if (difftime < 0) difftime = origctx.getStartTime();
         break;
       }
      if (oval != null) lastdiff = oval.getStartTime();
    }
   
   if (difftime > 0) {
      if (data_change <= 0 || data_change > difftime) {
         data_change = difftime;
         original_data_context = origctx;
         match_data_context = matchctx;
       }
    }
}



private List<DiexecuteValue> getVariableValues(DiexecuteCall ctx,DiexecuteVariable var)
{
   if (var == null) return new ArrayList<>();
   
   return var.getValues(ctx.getTrace()); 
}



private boolean matchTime(long orig,long match)
{
   if (test_match) return true;
   return orig == match;
}



private void noteChange(DiexecuteCall origctx,DiexecuteCall matchctx,long when)
{
   if (control_change > 0 && when > control_change) return;
   control_change = when;
   original_change_context = origctx;
   match_change_context = matchctx;
}



/********************************************************************************/
/*                                                                              */
/*      Match two contexts                                                      */
/*                                                                              */
/********************************************************************************/

private DiffStruct computeContextDiffs(DiexecuteCall origctx,DiexecuteCall matchctx)
{
   List<DiexecuteCall> a = origctx.getInnerCalls();
   List<DiexecuteCall> b = matchctx.getInnerCalls();
   
   int m = a.size();
   int n = b.size();
   int maxd = m + n;
   int origin = maxd;
   int [] lastd = new int[2*maxd+2];
   DiffStruct [] script = new DiffStruct [2*maxd+2];
   DiffStruct rslt = null;
   
   int row = 0;
   while (row < m && row < n && matchContext(a.get(row),b.get(row))) row++;
   
   int col = 0;
   lastd[0+origin] = row;
   script[0+origin] = null;
   
   int lower = (row == m ? origin+1 : origin-1);
   int upper = (row == n ? origin-1 : origin+1);
   if (lower > upper) return null;
   
   for (int d = 1; d <= maxd; ++d) {
      for (int k = lower; k <= upper; k+= 2) {
	 if (k == origin-d || (k != origin+d && lastd[k+1] >= lastd[k-1])) {
	    row = lastd[k+1] + 1;
	    script[k] = new DiffStruct(script[k+1],true,null,row-1);
	  }
	 else {
	    row = lastd[k-1];
	    script[k] = new DiffStruct(script[k-1],false,b.get(row+k-origin-1),row);
	  }
	 col = row + k - origin;
	 while (row < m && col < n && matchContext(a.get(row),b.get(col))) {
	    ++row;
	    ++col;
	  }
	 lastd[k] = row;
	 if (row == m && col == n) {
	    rslt = script[k].createEdits();
	    return rslt;
	  }
	 if (row == m) lower = k+2;
	 if (col == n) upper = k-2;
       }
      lower = lower-1;
      upper = upper+1;
    }
   
   return rslt;
}  



private static boolean matchContext(DiexecuteCall octx,DiexecuteCall mctx)
{
   String omthd = octx.getMethod();
   String mmthd = mctx.getMethod();
   
   return omthd.equals(mmthd);
}


private static class DiffStruct {

private int delete_count;
private DiexecuteCall replace_data;
private int line_index;
private DiffStruct next_edit;

DiffStruct(DiffStruct prior,boolean del,DiexecuteCall dat, int i) {
   next_edit = prior;
   delete_count = (del ? 1 : 0);
   replace_data = dat;
   line_index = i;
}

public int getNumDelete()		{ return delete_count; }

public DiexecuteCall getData()	{ return replace_data; }

public int getIndex()		{ return line_index; }

public DiffStruct getNext()		{ return next_edit; }

DiffStruct createEdits() {
   DiffStruct shead = this;
   DiffStruct ep = null;
   DiffStruct behind = null;
   while (shead != null) {
      behind = ep;
      if (ep != null && ep.delete_count > 0 && shead.delete_count > 0 &&
            ep.line_index == shead.line_index + 1) {
         shead.delete_count += ep.delete_count;
         behind = ep.next_edit;
       }
      ep = shead;
      shead = shead.next_edit;
      ep.next_edit = behind;
    }
   return ep;
}

}	// end of inner class DiffStruct



}       // end of class DiexecuteMatcher




/* end of DiexecuteMatcher.java */

