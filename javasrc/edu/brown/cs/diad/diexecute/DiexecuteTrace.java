/********************************************************************************/
/*                                                                              */
/*              DiexecuteTrace.java                                             */
/*                                                                              */
/*      description of class                                                    */
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadDataType;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadLocalVariable;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteTrace implements DiadTrace, DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Element         seede_result;
private long            problem_time;
private DiexecuteCall    problem_context;
private Map<Integer,Element> id_map;
private DiadThread       for_thread;
private Map<Element,DiexecuteCall> call_map;
private Map<String,DiexecuteCall> callid_map;
private String          session_id;
private DiexecuteExecution for_exec;
private Set<String>     ignore_names;

private static final Pattern UUID_PATTERN = Pattern.compile("\\p{XDigit}{8}");



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteTrace(DiexecuteExecution exec,Element rslt,DiadThread thrd)
{
   for_exec = exec;
   seede_result = IvyXml.getChild(rslt,"CONTENTS");
   session_id = IvyXml.getAttrString(rslt,"ID");
   problem_time = -1;
   problem_context = null;
   for_thread = thrd;
   call_map = new HashMap<>();
   callid_map = new HashMap<>();
   setupIdMap();
   DiexecuteCall root = getRootContext();
   callid_map.put("0",root);
   callid_map.put("*",root);
   callid_map.put("-1",root);
   
   ignore_names = new HashSet<>();
   DicontrolMain diad = exec.getContext().getManager().getDiadControl();
   String ws = diad.getSourceManager().getWorksapceShortName();
   String ign = diad.getProperty("Diad." + ws + ".ignore");
   if (ign != null) {
      StringTokenizer tok = new StringTokenizer(ign," \t,;");
      while (tok.hasMoreTokens()) {
         String ig = tok.nextToken();
         ignore_names.add(ig);
       }
    }
}




/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public long getSymptomTime()   
{
   return problem_time;
}


@Override public DiexecuteCall getSymptomContext()
{
   return problem_context;
}


@Override public DiexecuteCall getRootContext() 
{
   Element runner = getRunner();
   
   return getCallForContext(IvyXml.getChild(runner,"CONTEXT"));
}


DiexecuteCall getCallForContext(Element ctx)
{
   if (ctx == null) return null;
   
   synchronized (call_map) {
      DiexecuteCall vc = call_map.get(ctx);
      if (vc == null) {
         vc = new DiexecuteCall(this,ctx); 
         call_map.put(ctx,vc);
         String idx = String.valueOf(vc.getContextId());
         callid_map.put(idx,vc);
       }
      return vc;
    }
}


DiadThread getThread()
{
   return for_thread;
}


DiadStackFrame getStartingFrame() 
{
   Element runner = getRunner();
   if (runner == null) return null;
   String fid = IvyXml.getAttrString(runner,"FRAME");
   if (fid == null) return null;
   for (DiadStackFrame frm : for_thread.getStack().getFrames()) {
      if (frm.getFrameId().equals(fid)) return frm;
    }
   return null;
}


@Override public DiexecuteVarVal getException()
{
   DiexecuteCall prob = getSymptomContext(); 
   if (prob != null) {
      DiexecuteVarVal thr = prob.getVariables().get("*THROWS*");
      if (thr != null) {
         return thr;
       }
    }
   
   Element runner = getRunner();
   Element ret = IvyXml.getChild(runner,"RETURN");
   String reason = IvyXml.getAttrString(ret,"REASON");
   if (reason == null) return null;
   if (reason.equals("EXCEPTION")) {
      Element vale = IvyXml.getChild(ret,"VALUE");
      return new DiexecuteVarVal(vale,null);
    }
   
   return null;
}



boolean isReturn()
{
   Element runner = getRunner();
   Element ret = IvyXml.getChild(runner,"RETURN");
   String reason = IvyXml.getAttrString(ret,"REASON");
   return reason.equals("RETURN");
}


boolean isCompilerError()
{
   Element runner = getRunner();
   Element ret = IvyXml.getChild(runner,"RETURN");
   String reason = IvyXml.getAttrString(ret,"REASON");
   return reason == null || 
         reason.equals("COMPILER_ERROR") || 
         reason.equals("ERROR");
}



@Override public DiexecuteVarVal getReturnValue()
{
   Element runner = getRunner();
   Element ret = IvyXml.getChild(runner,"RETURN");
   String reason = IvyXml.getAttrString(ret,"REASON");
   if (reason == null) return null;
   if (reason.equals("RETURN")) {
      Element rval = IvyXml.getChild(ret,"VALUE");
      if (rval == null) return null;
      return new DiexecuteVarVal(rval,null); 
    }
   
   return null;
}


String getExceptionType() 
{
   Element runner = getRunner();
   Element ret = IvyXml.getChild(runner,"RETURN");
   String reason = IvyXml.getAttrString(ret,"REASON");
   if (reason == null) return null;
   if (reason.equals("EXCEPTION")) {
      return IvyXml.getAttrString(ret,"MESSAGE");
    }
   return null;
}


long getExceptionTime()
{
   DiexecuteCall prob = getSymptomContext();
   if (prob != null) {
      DiexecuteVarVal thr = prob.getVariables().get("*THROWS*");
      if (thr != null) {
         long when = prob.getEndTime();
         for (Long t : thr.getTimeChanges()) {
            if (t > 0 && t < when) when = t;
          }
         return when;
       }
    }
   
   Element runner = getRunner();
   Element ret = IvyXml.getChild(runner,"RETURN");
   String reason = IvyXml.getAttrString(ret,"REASON");
   if (reason != null && reason.equals("EXCEPTION")) {
      DiexecuteCall vc = getRootContext();
      return vc.getEndTime();
    }
   
   return -1;
}

long getExecutionTime()
{
   return IvyXml.getAttrLong(seede_result,"TICKS");
}


private Element getRunner()
{
   if (for_thread !=  null) {
      String tidx = for_thread.getThreadId();
      for (Element runner : IvyXml.children(seede_result,"RUNNER")) {
         String tid = IvyXml.getAttrString(runner,"THREAD");
         if (!tid.equals(tidx)) continue;
         return runner;
       }
    }
   
   Element runner = IvyXml.getChild(seede_result,"RUNNER");
   return runner;
}



@Override public Map<String,DiadTraceVarVal> getGlobalVariables()
{
   Map<String,DiadTraceVarVal> rslt = new LinkedHashMap<>();
   Element glbls = IvyXml.getChild(seede_result,"GLOBALS");
   for (Element e : IvyXml.children(glbls,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      rslt.put(nm,new DiexecuteVarVal(e,null));
    }
   
   return rslt;
}


@Override public String getSessionId()
{
   return session_id; 
}


DiexecuteCall getContextForTime(long when)
{
    return findContextForTime(getRootContext(),when);
}


private DiexecuteCall findContextForTime(DiexecuteCall call,long when)
{
   for (DiexecuteCall inner : call.getInnerCalls()) {
      if (inner.getStartTime() <= when && inner.getEndTime() > when) {
         return findContextForTime(inner,when);
       }
    }
   return call;
}


DiexecuteManager getManager()
{
   return for_exec.getContext().getManager();
}


private DiexecuteCall getCallFromId(String callid)
{
   if (callid == null || callid.isEmpty() || callid.equals("0")) {
      return getRootContext();
    }
   
   DiexecuteCall ctx = callid_map.get(callid);
   if (ctx == null) {
      IvyLog.logE("DIEXECUTE","Bad context id given " + callid);
      ctx = getRootContext();
      try {
         Integer.parseInt(callid);
       }
      catch (NumberFormatException e) {
         DiexecuteCall findctx = findContextByName(ctx,callid);
         if (findctx != null) ctx = findctx;
       }
    }
   
   return ctx;
}





private DiexecuteCall findContextByName(DiexecuteCall call,String cid) 
{
   if (call.getMethod().equals(cid)) {
      return call;
    }
   else if (call.getMethod().startsWith(cid + "(")) {
      return call;
    }
   for (DiexecuteCall icall : call.getInnerCalls()) {
      DiexecuteCall rcall = findContextByName(icall,cid);
      if (rcall != null) return rcall;
    }
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Find the point in the execution corresponding to the thread             */
/*                                                                              */
/********************************************************************************/

void setupForLaunch(DiadThread thread)
{
   if (problem_time >= 0 || seede_result == null) return;
   for_thread = thread;

   String tidx = for_thread.getThreadId();   
   Stack<String> stack = new Stack<>();
   for (Element runner : IvyXml.children(seede_result,"RUNNER")) {
      String tid = IvyXml.getAttrString(runner,"THREAD");
      if (!tid.equals(tidx)) continue;
      findProblemTime(IvyXml.getChild(runner,"CONTEXT"),thread,stack);
    }
}



void findProblemTime(Element ctx,DiadThread thread,Stack<String> stack)
{
   String mthd = IvyXml.getAttrString(ctx,"METHOD");
   stack.push(normalizeName(mthd));
   
   if (checkStack(thread,stack)) {
      findContextTime(ctx,thread);
    }
   else {
      for (Element subctx : IvyXml.children(ctx,"CONTEXT")) {
         findProblemTime(subctx,thread,stack);
       }
    }
   
   stack.pop();
}



private boolean checkStack(DiadThread thread,Stack<String> stack)
{
   DiadStack stk = thread.getStack();
   String base = stack.get(0);
   DiadStackFrame sfrm = getStartingFrame();
   List<DiadStackFrame> frms = stk.getFrames();
   
   if (sfrm != null) {
      int i1 = frms.indexOf(sfrm);
      if (i1 >= 0) {
         return checkStack(thread,stack,i1);
       }
    }
   
   // find the frame corresponding to our starting point
   for (int i = frms.size()-1; i >= 0; --i) {
      DiadStackFrame frame = frms.get(i);
      String sgn = frame.getFormatSignature();
      String id = frame.getClassName() + "." + frame.getMethodName() + sgn;
      id = normalizeName(id);
      IvyLog.logD("DIEXECUTE","Check stack " + id + " " + base);
      if (id.equals(base)) {
         return checkStack(thread,stack,i);
       }
    }
   return false;
}



private String normalizeName(String mthd)
{
   StringBuffer buf = new StringBuffer();
   int lvl = 0;
   for (int i = 0; i < mthd.length(); ++i) {
      char c = mthd.charAt(i);
      if (c == '<') {
         ++lvl;
         continue;
       }
      else if (c == '>') {
         --lvl;
         continue;
       }
      else if (lvl > 0) continue;
      else if (c == '$') c = '.';
      buf.append(c);
    }
   return buf.toString();
}


private boolean checkStack(DiadThread thread,Stack<String> stack,int start)
{ 
   DiadStack stk = thread.getStack(); 
   DiadStackFrame topframe = stk.getTopFrame();
   List<DiadStackFrame> frms = stk.getFrames();
   for (int i = start; i >= 0; --i) {
      DiadStackFrame frm = frms.get(i);
      String id = frm.getClassName() + "." + frm.getMethodName() + 
            frm.getFormatSignature();
      id = normalizeName(id);
      IvyLog.logD("DIEXECUTE","Check Stack " + i + " " + id + " " +
            start + " " + stack.size());
      if (start-i >= stack.size()) return false;
      IvyLog.logD("DIEXECUTE","Compare stack " + id + " " + 
            stack.get(start-i));
      if (!id.equals(stack.get(start-i))) return false;
      if (frm.equals(topframe)) return true;
    }
   return false;
}



private void findContextTime(Element ctx,DiadThread thread)
{
   Element linevar = null;
   for (Element var : IvyXml.children(ctx,"VARIABLE")) {
      String varname = IvyXml.getAttrString(var,"NAME");
      if (varname.equals("*LINE*")) {
         linevar = var;
         break;
       }
    }
   if (linevar == null) return;
   
   DiadStackFrame frame = thread.getStack().getTopFrame();
   String lno = Integer.toString(frame.getLineNumber());
   int lnoi = Integer.parseInt(lno);
   long linetime = -1;
   for (Element val : IvyXml.children(linevar,"VALUE")) {
      long time = IvyXml.getAttrLong(val,"TIME");
      if (time == 0) time = IvyXml.getAttrLong(ctx,"START"); 
      if (linetime > 0) {
         findContextTime(ctx,thread,lnoi,linetime,time-1);
         linetime = -1;
       }
      // ACTUALLY NEED TO CHECK IF ERROR STATEMENT SPANS MULTIPLE LINES
      if (lno.equals(IvyXml.getText(val))) {
         linetime = time;
       }
    }
   if (linetime > 0) {
      findContextTime(ctx,thread,lnoi,linetime,IvyXml.getAttrLong(ctx,"END"));
    }
}



private void findContextTime(Element ctx,DiadThread thread,int line,long from,long to)
{
   // check local variables in the context vs those of the thread
   DiadStackFrame frame = thread.getStack().getTopFrame();
   for (String var : frame.getLocals()) {
      DiadLocalVariable local = frame.getLocal(var);
      Element varelt = findVariableInContext(ctx,var,line);
      if (varelt != null) {
         long prev = -1;
         Element prevval = null;
         boolean found = false;
         int foundct = 0;
         for (Element valelt : IvyXml.children(varelt,"VALUE")) {
            long time = IvyXml.getAttrLong(valelt,"TIME");
            if (prev > 0) {
               if (time >= from && prev <= to) {
                  Boolean fg = compareVariable(local,prevval,
                        thread,from,to);
                  if (fg != null) {
                     ++foundct;
                     found |= fg;
                   }
                }
             }
            prev = time;
            prevval = dereference(valelt);
          }
         if (prev > 0) {
            long time = IvyXml.getAttrLong(ctx,"END");
            if (time >= from && prev <= to) {
               Boolean fg = compareVariable(local,prevval,
                     thread,from,to);
               if (fg != null) {
                  ++foundct;
                  found |= fg;
                }
             }
          }
         else if (prev == -1) {
            Boolean fg = compareVariable(local,prevval,
                  thread,from,to);
            if (fg != null) {
               ++foundct;
               found |= fg;
             }
          }
         if (foundct > 0 && !found)
            return;
       }
    }
   
   if (problem_time > 0 && problem_context !=  null) {
      // see if this context is better than saved context
    }
   
   problem_time = from;
   problem_context = getCallForContext(ctx);
}


private Element findVariableInContext(Element ctx,String nm,int lno)
{
   Element best = null;
   int bestln = -1;
   for (Element varelt : IvyXml.children(ctx,"VARIABLE")) {
      String varnam = IvyXml.getAttrString(varelt,"NAME");
      if (varnam.equals(nm)) {
         int vln = IvyXml.getAttrInt(varelt,"LINE");
         if (vln > lno) continue;
         if (best == null || vln > bestln) {
            bestln = vln;
            best = varelt;
          }
       }
    }
   
   return best;
}



/********************************************************************************/
/*                                                                              */
/*      Compare variables in execution with those in thread                     */
/*                                                                              */
/********************************************************************************/

private Boolean compareVariable(DiadLocalVariable local,Element valelt,
      DiadThread thread,long from,long to)
{
   if (ignore_names.contains(local.getName())) {
      return null;
    }
      
   switch (local.getKind()) {
      case "PRIMITIVE" :
         String typ = IvyXml.getAttrString(valelt,"TYPE");
         String valtxt = IvyXml.getText(valelt);
         String lclval = local.getValue();
         switch (typ) {
            case "boolean" :
               if (valtxt.equals("0") && lclval.equals("false")) return true;
               if (valtxt.equals("1") && lclval.equals("true")) return true;
               return false;
            case "double" :
            case "float" :
               if (lclval.equals(valtxt)) return true;
               try {
                  double v1 = Double.valueOf(lclval);
                  double v2 = Double.valueOf(valtxt);
                  if (Math.abs(v1-v2) < 0.0000001) return true;
                }
               catch (NumberFormatException e) { }
               return false;
            case "char" :
               int c1 = 0;
               String s1 = lclval;
               if (s1 != null && s1.length() > 0) c1 = s1.charAt(0); 
               int c2 = Integer.parseInt(valtxt);
               return c1 == c2;
            default :
               return lclval.equals(valtxt);
          }
      case "STRING" :
         valtxt = IvyXml.getText(valelt);
         String ltxt = local.getValue();
         return compareStrings(ltxt,valtxt);
      case "ARRAY" :
         if (local.getType().equals("null")) {
            if (IvyXml.getAttrBool(valelt,"NULL")) return true;
            return false;
          }   
         return compareArray(local,valelt,thread,from,to);
      case "OBJECT" :
         if (local.getType().equals("null") || local.getType().equals("*ANY*")) {
            if (IvyXml.getAttrBool(valelt,"NULL")) return true;
            return false;
          }
         else if (local.getType().equals("java.lang.Class")) {
            return true;
          }
         return compareObject(local,valelt,thread,from,to);
      case "CLASS" :
         System.err.println("CHECK HERE compare CLASS");
         break;
      default :
         break;
    }
   
   return null;
}



private Boolean compareObject(DiadLocalVariable local,Element valelt0,
      DiadThread thread,long from,long to)
{
   Element valelt = dereference(valelt0);
   if (local.getType().equals("null")) {
      if (IvyXml.getAttrBool(valelt,"NULL")) return true;
      return false;
    }
   
   String ltyp = local.getType();
   String vtype = IvyXml.getAttrString(valelt,"TYPE");
   if (!ltyp.equals(vtype)) {
      int idx = ltyp.indexOf("<");
      if (idx > 0) {
         ltyp = ltyp.substring(0,idx);
         ltyp = ltyp.replace("$",".");
         if (!ltyp.equals(vtype)) return false;
       }
    }
   
   DiadValue localval = thread.evaluate(local.getName());
   if (localval == null) return null;
   
   int ct = 0;
   for (Element fldelt : IvyXml.children(valelt,"FIELD")) {
      String nm = IvyXml.getAttrString(fldelt,"NAME");
      if (nm.startsWith("@")) continue;
      if (!ignore_names.isEmpty()) {
         if (ignore_names.contains(nm)) continue;
         int idx = nm.lastIndexOf(".");
         if (idx > 0) {
            String n1 = nm.substring(idx+1);
            if (ignore_names.contains(n1)) continue;
          }
       }
      try {
         DiadValue fldval = localval.getFieldValue(nm);
         if (fldval == null) continue;
         Boolean fg = checkValueAtTime(fldval,fldelt,
               thread,from,to);
         if (fg == null) continue;
         if (!fg) {
            IvyLog.logI("DIEXECUTE","Matching failed for " + nm);
            return false;
          }
         ++ct;  // if matched
       }
      catch (DiadException e) { }
    }
   
   if (ct > 0) return true;
   
   return null;
}



private Boolean compareArray(DiadLocalVariable local,Element valelt0,
      DiadThread thread,long from,long to)
{
   Element valelt = dereference(valelt0);
   if (local.getType().equals("null")) {
      if (IvyXml.getAttrBool(valelt,"NULL")) return true;
      return false;
    }
   
   String s1 = normalizeName(local.getType());
   String s2 = normalizeName(IvyXml.getAttrString(valelt,"TYPE"));
   if (!s1.equals(s2)) return false;
   
// DiadValue localval = thread.evaluate(local.getName());
// int ctxsz = IvyXml.getAttrInt(valelt,"SIZE");
   
   // check number of elements
   // loop for each element
   
   return null;
}



private Boolean checkValueAtTime(DiadValue actval,Element valctx,
      DiadThread thread,long from,long to)
{
   long prev = -1;
   Element prevval = null;
   int foundct = 0;
   boolean found = false;
   for (Element valelt : IvyXml.children(valctx,"VALUE")) {
      long time = IvyXml.getAttrLong(valelt,"TIME");
      if (prev >= 0) {
         if (time >= from && prev <= to) {
            Boolean fg = compareValueAtTime(actval,prevval,thread,from,to);
            if (fg != null) {
               ++foundct;
               found |= fg;
             }
          }
       }
      prev = time;
      prevval = valelt;
    }
   if (prev > 0 && prev <= to) {
      if (prev <= to) {
         Boolean fg = compareValueAtTime(actval,prevval,thread,from,to);
         if (fg != null) {
            ++foundct;
            found |= fg;
          }
       }
    }
   else if (prev == -1) {
      Boolean fg = compareValueAtTime(actval,prevval,thread,from,to);
      if (fg != null) {
         ++foundct;
         found |= fg;
       }   
    }
   
   if (foundct > 0 && !found) return false;
   if (found) return true;
   
   return null;
}



private Boolean compareValueAtTime(DiadValue actval,Element valctx,DiadThread thread,long from,long to)
{
   String ctxval = IvyXml.getText(valctx);
   String ctxtyp = IvyXml.getAttrString(valctx,"TYPE");
   DiadDataType typ = actval.getDataType();
   
   if (actval.isNull()) {
      return IvyXml.getAttrBool(valctx,"NULL");
    }
   if (ctxval == null) ctxval = "";
   
   // handle primitive types
   switch (typ.getName()) {
      case "boolean" :
         if (actval.getBoolean()) return ctxval.equals("1");
         else return ctxval.equals("0");
      case "int" :
      case "long" :
      case "short" :
      case "byte" :
      case "char" :
         try {
            long l = Long.parseLong(ctxval);
            return l == actval.getInt();
          }
         catch (NumberFormatException e) { }
         return null;
      case "double" :
      case "float" :
         return null;
      case "java.lang.String" :
         if (ctxtyp.equals("java.lang.String")) {
            return compareStrings(actval.getString(),ctxval);
          }
         break;
    }
   
   String s1 = typ.getName(); 
   int idx1 = s1.indexOf("<");
   if (idx1 > 0) s1 = s1.substring(0,idx1);
   String s2 = ctxtyp;
   int idx2 = s2.indexOf("<");
   if (idx2 > 0) s2 = s2.substring(0,idx2);
   s1 = s1.replace("$",".");
   
   if (!s1.equals(s2)) {
      if ((s1.endsWith("Set") || s1.endsWith("SetN")) &&
            (s2.endsWith("Set") || s2.endsWith("SetN"))) {
         return null; 
       }
      return false;
    }
   
   // handle objects and arrays when nested -- ignore for now
   
   return null;
} 



private Boolean compareStrings(String s1,String s2)
{
   if (s1 == null && s2 == null) return true;
   if (s1 == null || s2 == null) return false;
   if (s1.equals(s2)) return true;
   
   // UTF-16 strings are not correct when reported by bedrock
   if (s1.getBytes().length != s1.length()) return null;
   if (s2.getBytes().length != s2.length()) return null;
   
   // Random UUIDs will differ
   Matcher m1 = UUID_PATTERN.matcher(s1);
   Matcher m2 = UUID_PATTERN.matcher(s2);
   if (m1.find() && m2.find()) return null;
   
   return false;
}



/********************************************************************************/
/*                                                                              */
/*      Setup mapping for ID matching                                           */
/*                                                                              */
/********************************************************************************/

private void setupIdMap()
{
   id_map = new HashMap<>();
   for (Element valelt : IvyXml.elementsByTag(seede_result,"VALUE")) {
      int id = IvyXml.getAttrInt(valelt,"ID");
      if (id < 0) continue;
      if (IvyXml.getAttrBool(valelt,"REF")) continue;
      Element use = id_map.get(id);
      if (use != null) continue;
      id_map.put(id,valelt);
    }
}


Element dereference(Element val)
{
   if (IvyXml.getAttrBool(val,"REF")) {
      int id = IvyXml.getAttrInt(val,"ID");
      return id_map.get(id);
    }
   
   return val;
}


/********************************************************************************/
/*                                                                              */
/*      Location methods                                                        */
/*                                                                              */
/********************************************************************************/

void getExecutedLocations(Set<String> rslt)
{
   DiexecuteCall vc = getRootContext();
   if (vc != null) vc.getExecutedLocations(rslt);
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

JSONObject getJsonLocalTrace(String callid)
{
   DiexecuteCall ctx = getCallFromId(callid);
   
   return ctx.getJsonExecTrace(false);  
}


JSONArray getJsonLineTrace(String callid)
{
   DiexecuteCall ctx = getCallFromId(callid);
   if (ctx == null) return null;
   
   return ctx.getJsonLineTrace(); 
}


JSONObject getJsonVarTrace(String callid,String var)
{
   DiexecuteCall ctx = getCallFromId(callid);
   
   if (ctx == null) ctx = getRootContext();
   
   int lno = -1;
   try {
      lno = Integer.parseInt(callid);
    }
   catch (NumberFormatException e) { }
   
   return ctx.getJsonVarTrace(var,lno);   
}


JSONObject getJsonVarHistory(String callid,String var,int line,long when0)
{
   DiexecuteCall ctx = getCallFromId(callid); 
   if (ctx == null) {
      JSONObject err = new JSONObject();
      err.put("ERROR","Invalid callid " + callid);
      return err;
    }
   
   long when = when0;
   DiexecuteVarVal linevar = ctx.getLineNumbers(); 
   when = getActualTime(ctx,linevar,when,line);
   if (when < 0) {
      IvyLog.logE("DIEXECUTE","Bad time computed " + when + " " + line +
            " " + when0);
      JSONObject err = new JSONObject();
      err.put("ERROR","Invalid time " + when + " " + line);
      return err;
    }
   
   DiexecuteVarVal execvar = ctx.getValueAtTime(this,var,when);
   if (execvar == null) {
      JSONObject err = new JSONObject();
      err.put("ERROR","Invalid variable " + var);
      return err;
    }
   
   DiexecuteVarHistory hist = new DiexecuteVarHistory(this,ctx,
         execvar,var,when); 
   
   JSONObject rslt = hist.process();  
   
   if (rslt == null) {
      JSONObject err = new JSONObject();
      err.put("ERROR","history not found");
      return err;
    }
   
   return rslt;
}


private long getActualTime(DiexecuteCall ctx,DiexecuteVarVal linevar,
      long when,int line) 
{
   if (when == 0) {
      when = ctx.getStartTime();
    }
   if (line <= 0 && when > 0) {
      line = linevar.getLineValue(when);
    }
   else if (line <= 0 && when < 0) {
      boolean fnd = false;
      for (Long t : linevar.getTimeChanges()) {
         fnd = true;
         when = t;
         line = linevar.getLineValue(when);
       }
      if (!fnd) return 0;
    }
   else if (line > 0 && when < 0) {
      DiexecuteVarVal lines = ctx.getLineNumbers();
      boolean next = false;
      for (Long t : lines.getTimeChanges()) {
         int lno = lines.getLineValue(t);
         if (lno == line) next = true;
         else if (next) {
            when = t - 1;
            break;
          }
       }
      if (when < 0 && ctx.getParentCall() == null) {
         long t = getSymptomTime();
         DiexecuteCall cc = getContextForTime(t);
         if (cc != ctx) {
            DiexecuteVarVal lv = cc.getLineNumbers();
            when = getActualTime(cc,lv,-1,line);
          }
       }
      if (when < 0) {
         if (!next) {
            IvyLog.logE("DIEXECUTE","Get actual time given bad line " + line + 
                  " " + when);
          }
         when = ctx.getEndTime() - 1;
       }
    }
   
   return when;
}

JSONObject getJsonVarValue(String callid,String var,int line,long when0)
{
   DiexecuteCall ctx = getCallFromId(callid);
   if (ctx == null) return null;
   
   DiexecuteVarVal linevar = ctx.getLineNumbers();
   long when = getActualTime(ctx,linevar,when0,line);
   if (when < 0) {
      IvyLog.logE("DIEXECUTE","Bad time computed " + when + " " + line +
            " " + when0);
      JSONObject err = new JSONObject();
      err.put("ERROR","No time or line given");
      return err;
    }
   
   DiexecuteVarVal execvar = ctx.getValueAtTime(this,var,when);
   if (execvar == null) {
      DiexecuteVarVal var0 = ctx.getTraceVarValueFlex(var,when);
      if (var0 != null) execvar = var0.getValueAtTime(this,when);
    }
   if (execvar == null) {
      JSONObject err = new JSONObject();
      err.put("ERROR","No such variable");
      return err;
    }
   
   JSONObject jo = new JSONObject();
   jo.put("NAME",var);
   jo.put("TIME",when);
   jo.put("METHOD",ctx.getMethod());
   jo.put("VALUE",
         execvar.toJsonValue(this,when,new HashSet<>()));
   
   return jo;
}


JSONArray getJsonMethodCalls(String method)
{
   JSONArray rslt = new JSONArray();
   
   addMethodCalls(problem_context.getCallId(),method,rslt);
   
   return rslt; 
}


private void addMethodCalls(String callid,String method,JSONArray rslt)
{
   DiexecuteCall call = callid_map.get(callid);
   if (call == null) return;
   if (matchMethod(method,call.getMethod())) {
      JSONObject jo = new JSONObject();
      jo.put("CALLID",callid);
      jo.put("START_TIME",call.getStartTime());
      jo.put("END_TIME",call.getEndTime());
      jo.put("METHOD",call.getMethod());
      DiexecuteCall par = call.getParentCall();
      if (par != null) jo.put("CALLER_CALLID",par.getCallId());
      rslt.put(jo);
    }
   for (DiexecuteCall c1 : call.getInnerCalls()) {
      addMethodCalls(c1.getCallId(),method,rslt);
    }
}


private boolean matchMethod(String user0,String seede0)
{
   String user = user0;
   String seede = seede0;
   
   if (user.equals(seede)) return true;
   if (!user.contains("(")) {
      int idx = seede.indexOf("(");
      if (idx >= 0) {
         seede = seede.substring(0,idx);
       }
    }
   if (user.equals(seede)) return true;
   if (!user.contains(".")) {
      int idx = seede.indexOf("(");
      if (idx < 0) idx = seede.length()-1;
      int idx1 = seede.lastIndexOf(".",idx);
      if (idx1 > 0) {
         seede = seede.substring(idx1+1);
       }
    }
   if (user.equals(seede)) return true;
   
   return false;
}



@Override public String toString()
{
   return IvyXml.convertXmlToString(seede_result);
}


}       // end of class DiexecuteTrace




/* end of DiexecuteTrace.java */

