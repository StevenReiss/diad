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

DiadValidationStatus check()
{
   if (check_execution == null) return DiadValidationStatus.COMPILER_ERROR;
   if (original_execution == null) return DiadValidationStatus.NO_BASE_EXECUTION;
   if (validate_context.getSymptom() == null) return DiadValidationStatus.CANT_VALIDATE;
   
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
         return DiadValidationStatus.CANT_VALIDATE;
      default :
      case NO_EXCEPTION :
         break;
    }
    
   DiadValidationStatus v0 = DiadValidationStatus.VALID_UNKNOWN; 
   if (vpc != null) v0 = vpc.validate();
   
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
   
   abstract DiadValidationStatus validate();
   
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
   
   @Override DiadValidationStatus validate() {
      if (!executionChanged()) return DiadValidationStatus.INVALID_NOCHANGE;
      if (exceptionThrown()) return DiadValidationStatus.INVALID_EXCEPTION; 
      
      DiexecuteVarVal origexc = original_execution.getException();
      if (origexc != null && execution_matcher.getMatchSymptomContext() != null) {
         DiexecuteVarVal checkexc = check_execution.getException();
         if (checkexc == null) {
            return DiadValidationStatus.VALID; 
          }
         else if (execution_matcher.getMatchSymptomAfterTime() > 0) {
            if (check_execution.getExceptionTime() > execution_matcher.getMatchSymptomAfterTime()) {
               return DiadValidationStatus.VALID_PROGRESS;
             }
          }
         else if (origexc.getDataType(-1).equals(checkexc.getDataType(-1))) {
            return DiadValidationStatus.INVALID_EXCEPTION;
          }
         else {
            return DiadValidationStatus.INVALID_NOCHANGE;   
          }
       }
      else if (execution_matcher.getMatchSymptomContext() != null) {
         return DiadValidationStatus.VALID_MAYBE;
       }
      
      if (check_execution.isReturn()) { 
         return DiadValidationStatus.VALID;
       }
      
      return DiadValidationStatus.INVALID_LIKELY; 
    }
   
   @Override boolean validateTestLocal() {
      DiexecuteVarVal origexc = original_execution.getException();
      if (origexc != null && execution_matcher.getMatchSymptomContext() != null) {
         DiexecuteVarVal checkexc = check_execution.getException();
         if (checkexc == null) {
            return false;
          }
         else {
            return origexc.getDataType(-1).equals(checkexc.getDataType(-1));
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
   
   @Override DiadValidationStatus validate() {
      if (!executionChanged()) return DiadValidationStatus.INVALID_NOCHANGE;
      if (exceptionThrown()) return DiadValidationStatus.INVALID_EXCEPTION;
      
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
         return DiadValidationStatus.INVALID_NOMATCH;
       }   
      DiexecuteVarVal vv = vc.getVariables().get(var);
      if (vv == null) {
         IvyLog.logD("DIEXECUTE","Variable not found in change context");
         return DiadValidationStatus.VALID_MAYBE;
       }
      
      long t0 = execution_matcher.getMatchSymptomTime();
      IvyLog.logD("DIEXECUTE","Match problem time " + t0);
      if (t0 > 0) {
         DiexecuteVarVal vval = vv.getValueAtTime(check_execution,t0);
         IvyLog.logD("DIEXECUTE","Value at time : " + vval);
         if (vval != null) {
            String vvalstr = vval.getStringValue(t0);
            if (oval == null && vvalstr == null) return DiadValidationStatus.INVALID_NOCHANGE;
            if (oval != null && oval.equals(vvalstr)) return DiadValidationStatus.INVALID_NOCHANGE;
            
            return matchValue(vval,vvalstr,nval); 
          }
       }
      boolean haveold = false;
      boolean haveother = false;
      for (Long t : vv.getTimeChanges()) {
         String vvalstr = vv.getStringValue(t);
         if (oval == null && vvalstr == null) {
            haveold = true;
            haveother = false;
          }     
         else if (oval != null && oval.equals(vvalstr)) haveold = true;
         else if (nval != null && !nval.equals(vvalstr)) {
            return DiadValidationStatus.VALID_LIKELY;
          }
         else if (nval == null && t > 0) haveother = true;
       }
      
      if (!haveold) return DiadValidationStatus.VALID_LIKELY;
      if (haveother) return DiadValidationStatus.VALID_MAYBE;
      
      return DiadValidationStatus.INVALID_NOCHANGE;
    }
   
   @Override boolean validateTestLocal() {
      long t1 = execution_matcher.getDataChangeTime();
      if (t1 <= 0 || t1 > execution_matcher.getSymptomAfterTime()) return true;
      return false;
    }
   
   private DiadValidationStatus matchValue(DiexecuteVarVal vval,String vvalstr,String nval) {
      if (nval == null) return DiadValidationStatus.VALID_LIKELY;
      
      IvyLog.logD("DIEXECUTE","Match values " + vvalstr + " " + nval + " " + vval.getDataType(-1));
      
      DiadSymptom prob = validate_context.getSymptom();
      
      if (nval.equals(vvalstr)) return DiadValidationStatus.VALID;
      if (vval.getDataType(-1).equals("float") || vval.getDataType(-1).equals("double")) {
         try {
            double v1 = Double.valueOf(vvalstr);
            double v2 = Double.valueOf(nval);
            double diff = Math.abs(v1-v2);
            if (diff <= prob.getTargetPrecision()) return DiadValidationStatus.VALID;
          }
         catch (NumberFormatException e) {
            // should handle > x, < x, ...
            return DiadValidationStatus.VALID_MAYBE;
          }
       }
      else if (vval.getDataType(-1).equals("int") || vval.getDataType(-1).equals("long")) {
         try {
            long v1 = Long.valueOf(vvalstr);
            long v2 = Long.valueOf(nval);
            if (v1 == v2) return DiadValidationStatus.VALID;
          }
         catch (NumberFormatException e) {
            // should handle > x, < x, ...
            return DiadValidationStatus.VALID_MAYBE;
          }
         // handle > x , < x , ...
       }
      else if (vval.getDataType(-1).equals("boolean")) {
         Boolean v1 = getBoolean(vvalstr);
         Boolean v2 = getBoolean(nval);
         if (v1 != null && v2 != null) {
            if (v1.equals(v2)) return DiadValidationStatus.VALID;
          }
       }
      else {
         // handle non-null, etc. 
       }
      
      return DiadValidationStatus.INVALID_NOMATCH;
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
   
   @Override DiadValidationStatus validate() {
      return DiadValidationStatus.INVALID_NOCHANGE;
    }                
   
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
   
   @Override DiadValidationStatus validate() {
      if (!executionChanged()) return DiadValidationStatus.INVALID_NOCHANGE;
      if (exceptionThrown()) return DiadValidationStatus.INVALID_EXCEPTION;
      long t0 = execution_matcher.getMatchSymptomTime();
      if (t0 < 0) return DiadValidationStatus.INVALID_LIKELY;
      return DiadValidationStatus.VALID_MAYBE;
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
   
   @Override DiadValidationStatus validate() {
      if (!executionChanged()) return DiadValidationStatus.INVALID_NOCHANGE;
      
      DiexecuteCall vc = execution_matcher.getMatchChangeContext();
      long t0 = execution_matcher.getMatchSymptomTime();
      if  (vc != null) {
         if (t0 <= 0) {
            if (exceptionThrown()) return DiadValidationStatus.INVALID_EXCEPTION;
            return DiadValidationStatus.VALID_LIKELY;
          }  
       }
      
      while (vc != null && t0 > vc.getEndTime()) {
         vc = vc.getParentCall();
       }
      
      if (vc == null) return DiadValidationStatus.VALID_MAYBE;
      
      DiexecuteVarVal vv = vc.getLineNumbers();
      int lmatch = vv.getLineValue(t0);
      if (lmatch <= 0) return DiadValidationStatus.VALID_LIKELY;
      
      DiexecuteCall ovc = (DiexecuteCall) execution_matcher.getSymptomContext();
      DiexecuteCall mvc = execution_matcher.getMatchSymptomContext();
      if (ovc == null) ovc = vc;
      if (mvc == null) return DiadValidationStatus.VALID_MAYBE;
      
      long t1 = execution_matcher.getSymptomTime();
      DiexecuteVarVal ovv = ovc.getLineNumbers();
      DiexecuteVarVal mvv = mvc.getLineNumbers();
      int olno = ovv.getLineValue(t1);
      int mlno = mvv.getLineValue(t0);
      
      if (mlno == olno) return DiadValidationStatus.INVALID_NOCHANGE;
      
      boolean fnd = false;
      for (Long t : mvv.getTimeChanges()) {
         int elno = mvv.getLineValue(t);
         if (elno == olno) fnd = true;
       }
      if (fnd) return DiadValidationStatus.VALID_MAYBE;
      
      return DiadValidationStatus.VALID_LIKELY;
    }
   
   @Override boolean validateTestLocal() {
      long t0 = execution_matcher.getMatchSymptomTime();
      DiexecuteCall vc = execution_matcher.getMatchChangeContext();
      DiexecuteVarVal vv = vc.getLineNumbers();
      int lmatch = vv.getLineValue(t0);
      if (lmatch <= 0) return false;
      return true;
    }
   
   
}       // end of inner class DiexecuteCheckerException



}       // end of class DiexecuteChecker




/* end of DiexecuteChecker.java */

