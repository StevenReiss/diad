/********************************************************************************/
/*                                                                              */
/*              DiexecuteChecker.java                                           */
/*                                                                              */
/*      Check if a new executeion is valid for given problem                    */
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

import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.ivy.file.IvyLog;

class DiexecuteChecker implements DiexecuteConstants
{
 

/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteBaseExecution validate_context;
private DiexecuteTrace         original_execution;
private DiexecuteTrace         check_execution;
private DiadRepair             for_repair;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteChecker(DiexecuteBaseExecution ctx,DiexecuteTrace orig,DiexecuteTrace check,DiadRepair repair)
{
   validate_context = ctx;
   original_execution = orig;
   check_execution = check;
   for_repair = repair;
}


/********************************************************************************/
/*                                                                              */
/*      Checking methods                                                        */
/*                                                                              */
/********************************************************************************/

double check()
{
   if (check_execution == null) return 0;
   if (original_execution == null) return 0.5;
   if (validate_context.getSymptom() == null) return 0;
// if (check_execution.getDiadContext() == null) return 0;
   
   DiexecuteMatcher matcher = new DiexecuteMatcher(original_execution,check_execution,for_repair,false);
   matcher.computeMatch();
   
   DiexecuteSymptomChecker vpc = null;
   switch (validate_context.getSymptom().getSymptomType()) {
      case EXCEPTION :
      case ASSERTION :
         vpc = new DiexecuteCheckerException(matcher,false);
         break;
      case EXPRESSION :
         vpc = new DiexecuteCheckerExpression(matcher,false);
         break;
      case LOCATION :
         vpc = new DiexecuteCheckerLocation(matcher,false);
         break;
      case VARIABLE :
         vpc = new DiexecuteCheckerVariable(matcher,false);
         break;
      case NONE :
         return 0;
      default :
      case NO_EXCEPTION :
      case CAUGHT_EXCEPTION :
         break;
    }
   
   double v0 = DEFAULT_SCORE; 
   if (vpc != null) v0 = vpc.validate();
   
   if (v0 != 0) {
      double v1 = matcher.getControlChangeTime();
      double v2 = matcher.getDataChangeTime();
      double v3 = matcher.getSymptomTime(); 
      double v4 = (v1 == 0 ? v2 : (v2 == 0 ? v1 : Math.min(v1,v2)));
      double v6 = v4/v3;
      v0 = (v0 * 0.95) + (v6 * 0.05);
    }
   
   return v0;
}


boolean checkTest()
{
   if (check_execution == null) return false;
   if (original_execution == null) return false;
   if (validate_context.getSymptom() == null) return false;
// if (check_execution.getDiadContext() == null) return false;
   
   DiexecuteMatcher matcher = new DiexecuteMatcher(original_execution,check_execution,null,true);
   matcher.computeMatch();
   
   DiexecuteSymptomChecker vpc = null;
   switch (validate_context.getSymptom().getSymptomType()) {
      case EXCEPTION :
      case ASSERTION :
         vpc = new DiexecuteCheckerException(matcher,true);
         break;
      case EXPRESSION :
         vpc = new DiexecuteCheckerExpression(matcher,true);
         break;
      case LOCATION :
         vpc = new DiexecuteCheckerLocation(matcher,true);
         break;
      case VARIABLE :
         vpc = new DiexecuteCheckerVariable(matcher,true);
         break;
      case NONE :
         vpc = new DiexecuteCheckerNone(matcher,true);
         break;
      default :
      case NO_EXCEPTION :
      case CAUGHT_EXCEPTION :     
         return false;
    }
   
   boolean fg = vpc.validateTest();
   
   return fg;
}


/********************************************************************************/
/*                                                                              */
/*      Helper methods                                                          */
/*                                                                              */
/********************************************************************************/

String fixValue(String val,String typ)
{
   if (val == null) return null;
   String rslt = val;
   int len = val.length();
   if (typ != null && typ.equals("java.lang.String") && 
         val.startsWith("\"") && val.endsWith("\"") && len >= 2) {
      rslt = val.substring(1,len-1);
    }
   
   if (val.equalsIgnoreCase("Non-Null")) return null;   // anything should work then
   
   return rslt;
}

/********************************************************************************/
/*                                                                              */
/*      General problem-specific checker                                        */
/*                                                                              */
/********************************************************************************/

private abstract class DiexecuteSymptomChecker {

   protected DiexecuteMatcher execution_matcher;
   
   
   protected DiexecuteSymptomChecker(DiexecuteMatcher m,boolean test) {
      execution_matcher = m;
    }
   
   abstract double validate();
   
   boolean validateTest() {
      long t0 = execution_matcher.getControlChangeTime();
      long t1 = execution_matcher.getDataChangeTime();
      long t2 = execution_matcher.getSymptomTime();
      if (t0 > 0 && t0 < t2) return false;
      if (t1 > 0 && t1 < t2) return false;
      boolean fg = validateTestLocal();
      return fg;
    }
   abstract boolean validateTestLocal();
   
   protected boolean executionChanged() {
      long t0 = execution_matcher.getControlChangeTime();
      long t1 = execution_matcher.getDataChangeTime();
      long t2 = execution_matcher.getSymptomAfterTime();
      if (!execution_matcher.repairExecuted()) return false;
      if (Math.min(t0,t1) > t2) return false;
      return true;
    }
   
   protected boolean exceptionThrown() {
      long t0 = execution_matcher.getControlChangeTime();
      long t2 = execution_matcher.getSymptomTime();
      long t3 = check_execution.getExceptionTime();
      long t4 = execution_matcher.getMatchSymptomTime();
      if (t0 < t2 && t4 <= 0 && t3 > 0 && t3 < t2) return true;
      
      return false;
    }
   
}       // end of inner class DiexecuteSymptomChecker



/********************************************************************************/
/*                                                                              */
/*      Checker for exception problems                                          */
/*                                                                              */
/********************************************************************************/

private class DiexecuteCheckerException extends DiexecuteSymptomChecker {
   
   DiexecuteCheckerException(DiexecuteMatcher m,boolean test) {
      super(m,test);
    }
   
   @Override double validate() {
      if (!executionChanged()) return 0;
      if (exceptionThrown()) return 0;
      
      DiexecuteValue origexc = original_execution.getException();
      if (origexc != null && execution_matcher.getMatchSymptomContext() != null) {
         DiexecuteValue checkexc = check_execution.getException();
         if (checkexc == null) {
            if (execution_matcher.getMatchSymptomAfterTime() > 0) return 1.0;
            return 0.8;
          }
         else if (execution_matcher.getMatchSymptomAfterTime() > 0) {
            if (check_execution.getExceptionTime() > execution_matcher.getMatchSymptomAfterTime()) {
               return 0.5;
             }
          }
         else if (origexc.getDataType().equals(checkexc.getDataType())) return 0;
         else return 0.1;   
       }
      else if (execution_matcher.getMatchSymptomContext() != null) return 0.5;
      
      if (check_execution.isReturn()) return 0.75;
      
      return 0.2;
    }
   
   @Override boolean validateTestLocal() {
      DiexecuteValue origexc = original_execution.getException();
      if (origexc != null && execution_matcher.getMatchSymptomContext() != null) {
         DiexecuteValue checkexc = check_execution.getException();
         if (checkexc == null) {
            return false;
          }
         else {
            return origexc.getDataType().equals(checkexc.getDataType());
          }
       }
      return false;
}

}       // end of inner class DiexecuteCheckerException




/********************************************************************************/
/*                                                                              */
/*      Checker for variable  problems                                          */
/*                                                                              */
/********************************************************************************/

private class DiexecuteCheckerVariable extends DiexecuteSymptomChecker {

   DiexecuteCheckerVariable(DiexecuteMatcher m,boolean test) {
      super(m,test);
    }
   
   @Override double validate() {
      if (!executionChanged()) return 0;
      if (exceptionThrown()) return 0;
      
      DiadSymptom prob = validate_context.getSymptom();
      String var = prob.getSymptomItem();
      String oval = prob.getOriginalValue();
      String otyp = null;
      int idx = -1;
      if (oval != null) idx = oval.indexOf(" ");
      if (idx > 0) {
         otyp = oval.substring(0,idx);
         oval = oval.substring(idx+1);
       }
      // might need to separate oval into type and v`alue
      String nval = prob.getTargetValue();
      // might need to change nval to null to indicate any other value
      oval = fixValue(oval,otyp);
      nval = fixValue(nval,otyp);
      IvyLog.logD("DIEXECUTE","Check variable values " + oval + " " + nval);
      
      DiexecuteCall vc = execution_matcher.getMatchSymptomContext();
      if (vc == null) {
         IvyLog.logD("DIEXECUTE","No change context for variable");
         return 0.0;
       }   
      DiexecuteVariable vv = vc.getVariables().get(var);
      if (vv == null) {
         IvyLog.logD("DIEXECUTE","Variable not found in change context");
         return 0.5;
       }
      
      long t0 = execution_matcher.getMatchSymptomTime();
      IvyLog.logD("DIEXECUTE","Match problem time " + t0);
      if (t0 > 0) {
         DiexecuteValue vval = vv.getValueAtTime(check_execution,t0);
         IvyLog.logD("DIEXECUTE","Value at time : " + vval);
         if (vval != null) {
            String vvalstr = vval.getValue();
            if (oval == null && vvalstr == null) return 0;
            if (oval != null && oval.equals(vvalstr)) return 0.0;
            
            return matchValue(vval,vvalstr,nval);
          }
       }
      boolean haveold = false;
      boolean haveother = false;
      for (DiexecuteValue vval : vv.getValues(check_execution)) {
         String vvalstr = vval.getValue();
         if (oval == null && vvalstr == null) {
            haveold = true;
            haveother = false;
          }     
         else if (oval != null && oval.equals(vvalstr)) haveold = true;
         else if (nval != null && !nval.equals(vvalstr)) {
            if (haveold) return 0.60;
            return 0.75;
          }
         else if (nval == null && vval.getStartTime() > 0) haveother = true;
       }
      
      if (!haveold) return 0.6;
      if (haveother) return 0.5;
      
      return 0.0;
    }
   
   @Override boolean validateTestLocal() {
      long t1 = execution_matcher.getDataChangeTime();
      if (t1 <= 0 || t1 > execution_matcher.getSymptomAfterTime()) return true;
      return false;
    }
   
   private double matchValue(DiexecuteValue vval,String vvalstr,String nval)
{
      if (nval == null) return 0.9;
      
      IvyLog.logD("DIEXECUTE","Match values " + vvalstr + " " + nval + " " + vval.getDataType());
      
      DiadSymptom prob = validate_context.getSymptom();
      
      if (nval.equals(vvalstr)) return 1.0;
      if (vval.getDataType().equals("float") || vval.getDataType().equals("double")) {
         try {
            double v1 = Double.valueOf(vvalstr);
            double v2 = Double.valueOf(nval);
            double diff = Math.abs(v1-v2);
            if (diff <= prob.getTargetPrecision()) return 1.0;
          }
         catch (NumberFormatException e) {
            // should handle > x, < x, ...
            return 0.6;
          }
       }
      else if (vval.getDataType().equals("int") || vval.getDataType().equals("long")) {
         try {
            long v1 = Long.valueOf(vvalstr);
            long v2 = Long.valueOf(nval);
            if (v1 == v2) return 1.0;
          }
         catch (NumberFormatException e) {
            // should handle > x, < x, ...
            return 0.6;
          }
         // handle > x , < x , ...
       }
      else if (vval.getDataType().equals("boolean")) {
         Boolean v1 = getBoolean(vvalstr);
         Boolean v2 = getBoolean(nval);
         if (v1 != null && v2 != null) {
            if (v1.equals(v2)) return 1.0;
          }
       }
      else {
         // handle non-null, etc. 
       }
      
      return 0.0;
    }
   
   private Boolean getBoolean(String s) {
      if (s == null || s.length() == 0) return null;
      return s.startsWith("tT1Yy");
    }
   
}       // end of inner class DiexecuteCheckerVariable



/********************************************************************************/
/*                                                                              */
/*      Check for NO Symptoms                                                   */
/*                                                                              */
/********************************************************************************/

private class DiexecuteCheckerNone extends DiexecuteSymptomChecker {
   
   DiexecuteCheckerNone(DiexecuteMatcher m,boolean test) {
      super(m,test);
    }
   
   @Override double validate()                  { return 0.0; }
   
   @Override boolean validateTestLocal() {
      long t1 = execution_matcher.getDataChangeTime();
      if (t1 <= 0 || t1 > execution_matcher.getSymptomAfterTime()) return true;
      // should check problem variables at the problem time rather than all variables
      return false;
    }

}       // end of inner class DiexecuteCheckerNone




/********************************************************************************/
/*                                                                              */
/*      Checker for exception problems                                          */
/*                                                                              */
/********************************************************************************/

private class DiexecuteCheckerExpression extends DiexecuteSymptomChecker {
   
   DiexecuteCheckerExpression(DiexecuteMatcher m,boolean test) {
      super(m,test);
    }
   
   @Override double validate() {
      if (!executionChanged()) return 0;
      if (exceptionThrown()) return 0;
      long t0 = execution_matcher.getMatchSymptomTime();
      if (t0 < 0) return 0.2;
      return 0.5;
    }
   
   @Override boolean validateTestLocal() {
      return true;
    }
   
}       // end of inner class DiexecuteCheckerException




/********************************************************************************/
/*                                                                              */
/*      Checker for exception problems                                          */
/*                                                                              */
/********************************************************************************/

private class DiexecuteCheckerLocation extends DiexecuteSymptomChecker {

   DiexecuteCheckerLocation(DiexecuteMatcher m,boolean test) {
      super(m,test);
    }
   
   @Override double validate() {
      if (!executionChanged()) return 0;
      
      DiexecuteCall vc = execution_matcher.getMatchChangeContext();
      long t0 = execution_matcher.getMatchSymptomTime();
      if  (vc != null) {
         if (t0 <= 0) {
            if (exceptionThrown()) return 0.2;
            return 0.8;
          }  
       }
      
      while (vc != null && t0 > vc.getEndTime()) {
         vc = vc.getParentCall();
       }
      
      if (vc == null) return 0.5;
      
      DiexecuteVariable vv = vc.getLineNumbers();
      int lmatch = vv.getLineAtTime(t0);
      if (lmatch <= 0) return 0.8;
      
      DiexecuteCall ovc = (DiexecuteCall) execution_matcher.getSymptomContext();
      DiexecuteCall mvc = execution_matcher.getMatchSymptomContext();
      if (ovc == null) ovc = vc;
      if (mvc == null) return 0.5;
      
      long t1 = execution_matcher.getSymptomTime();
      DiexecuteVariable ovv = ovc.getLineNumbers();
      DiexecuteVariable mvv = mvc.getLineNumbers();
      int olno = ovv.getLineAtTime(t1);
      int mlno = mvv.getLineAtTime(t0);
      
      if (mlno == olno) return 0;
      
      boolean fnd = false;
      for (DiexecuteValue mv : mvv.getValues(check_execution)) {
         int elno = mv.getLineValue();
         if (elno == olno) fnd = true;
       }
      if (fnd) return 0.6;
      
      return 0.9;
    }
   
   @Override boolean validateTestLocal() {
      long t0 = execution_matcher.getMatchSymptomTime();
      DiexecuteCall vc = execution_matcher.getMatchChangeContext();
      DiexecuteVariable vv = vc.getLineNumbers();
      int lmatch = vv.getLineAtTime(t0);
      if (lmatch <= 0) return false;
      return true;
    }
   
   
}       // end of inner class DiexecuteCheckerException



}       // end of class DiexecuteChecker




/* end of DiexecuteChecker.java */

