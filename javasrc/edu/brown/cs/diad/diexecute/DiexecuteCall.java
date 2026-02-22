/********************************************************************************/
/*										*/
/*		DiexecuteCall.java						*/
/*										*/
/*	description of class							*/
/*										*/
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.diad.diexecute;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceCall;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteCall implements DiadTraceCall
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private Element 	context_element;
private DiexecuteTrace	for_trace;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

DiexecuteCall(DiexecuteTrace vt,Element ctx)
{
   for_trace = vt;
   context_element = ctx;
}


/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

@Override public String getMethod()
{
   return IvyXml.getAttrString(context_element,"METHOD");
}


@Override public File getFile()
{
   String fnm = IvyXml.getAttrString(context_element,"FILE");
   if (fnm == null) return null;
   return new File(fnm);
}


@Override public long getStartTime()
{
   return IvyXml.getAttrLong(context_element,"START");
}


@Override public long getEndTime()
{
   return IvyXml.getAttrLong(context_element,"END");
}

@Override public int getContextId()
{
   return IvyXml.getAttrInt(context_element,"ID");
}

@Override public String getCallId()
{
   return IvyXml.getAttrString(context_element,"ID");
}

boolean sameAs(DiexecuteCall call)
{
   if (call == null) return false;

   return getContextId() == call.getContextId();
}


DiexecuteTrace getTrace()
{
   return for_trace;
}


List<DiexecuteCall> getInnerCalls()
{
   List<DiexecuteCall> rslt = new ArrayList<>();
   for (Element c : IvyXml.children(context_element,"CONTEXT")) {
      rslt.add(for_trace.getCallForContext(c));
    }
   return rslt;
}


@Override public List<DiadTraceCall> getInnerTraceCalls()
{
   List<DiadTraceCall> rslt = new ArrayList<>();
   for (Element c : IvyXml.children(context_element,"CONTEXT")) {
      rslt.add(for_trace.getCallForContext(c));
    }
   return rslt;
}



@Override public DiexecuteVarVal getLineNumbers()
{
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals("*LINE*")) return new DiexecuteVarVal(e,null);
    }

   return null;
}


@Override public DiexecuteCall getParentCall()
{
   for (Node n = context_element.getParentNode(); n != null; n = n.getParentNode()) {
      if (IvyXml.isElement(n,"CONTEXT")) {
	 return  for_trace.getCallForContext((Element) n);
       }
    }
   return null;
}


Map<String,DiexecuteVarVal> getVariables()
{
   Map<String,DiexecuteVarVal> rslt = new LinkedHashMap<>();
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals("*LINE*")) continue;
      rslt.put(nm,new DiexecuteVarVal(e,null));
    }
   return rslt;
}


@Override public Map<String,DiadTraceVarVal> getTraceVariables()
{
   Map<String,DiadTraceVarVal> rslt = new LinkedHashMap<>();
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals("*LINE*")) continue;
      rslt.put(nm,new DiexecuteVarVal(e,null));
    }
   return rslt;
}


DiexecuteVarVal getTraceVariable(String name)
{
   String mnm = name;
   int idx = name.indexOf("@");
   int line = -1;
   if (idx > 0) {
      mnm = name.substring(0,idx);
      line = Integer.parseInt(name.substring(idx+1));
    }

   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals(mnm)) {
	 if (line > 0) {
	    int lno = IvyXml.getAttrInt(e,"LINE");
	    if (lno > 0 && lno != line) continue;
	  }
	 return new DiexecuteVarVal(e,null);
       }
    }

   return null;
}

DiexecuteVarVal getTraceVarValueFlex(String name,long when)
{
   if (name == null) return null;
   
   int idx = name.lastIndexOf("?");
   if (idx > 0) {
      // handle ? sequence of names
      String pre = name.substring(0,idx);
      String sub = name.substring(idx+1);
      DiexecuteVarVal v0 = getTraceVarValueFlex(pre,when);
      if (v0 == null) return null;
      v0 = v0.getValueAtTime(for_trace,when);
      DiexecuteVarVal v1 = v0.getChild(sub,when);
      if (v1 != null) {
	 v1 = v1.dereference(for_trace);
	 return v1;
       }
      else if (when < getEndTime()) {
	 return getTraceVarValueFlex(name,getEndTime());
       }
      else return null;
    }

   // Look up the name directly and use if found
   DiexecuteVarVal var0 = getTraceVariable(name);
   if (var0 != null) return var0;

   // look up this.name and use if found
   DiexecuteVarVal thisv = getTraceVariable("this");
   if (thisv != null) {
      thisv = thisv.getValueAtTime(for_trace,getStartTime()+1);
      if (thisv != null) {
         DiexecuteVarVal var1 = thisv.getChild(name,when);
         if (var1 != null) return var1;
       }
    }
   
   // replace name.field with name?field
   // replace name[idx] with name?[idx]
   String name1 = name;
   name1 = name1.replace(".","?");
   name1 = name1.replace("[","?[");
   if (!name1.equals(name)) {
      // if either replacement worked, then try with new value
      return getTraceVarValueFlex(name1,when);
    }
   
   
   // try a different context if given starting context -- use end context?
   if (getParentCall() == null) {
      long t = for_trace.getSymptomTime();
      DiexecuteCall cc = for_trace.getContextForTime(t);
      if (cc != this) {
         return cc.getTraceVarValueFlex(name,t);
       }
      
      // find correct context
    }
   
   return null;
}




DiexecuteVarVal getValueAtTime(DiadTrace trace,String name,long when)
{
   DiexecuteVarVal var0 = getTraceVarValueFlex(name,when);
   if (var0 == null && when < getEndTime()) {
      return getValueAtTime(trace,name,getEndTime());
    }
   else if (var0 == null) {
      IvyLog.logE("DIEXECUTE","Variable not found " + name + " " + when +
	    " " + IvyXml.convertXmlToString(context_element));
      return null;
    }
   else {
      var0 = var0.getValueAtTime(for_trace,when);
    }

   if (var0 == null) {
      IvyLog.logE("DIEXECUTE","Empty value at time " + name + " " + when +
	    " " + IvyXml.convertXmlToString(context_element));
    }

   return var0;
}



String getVariableName(String id,int lno)
{
   String rslt = null;
   int bestline = 0;

   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      DiexecuteVarVal xvar = new DiexecuteVarVal(e,null);
      String s = xvar.getName();
      if (s.startsWith("*")) continue;
      String var = s;
      int vln = 0;
      int idx = s.indexOf("@");
      if (idx > 0) {
	 vln = Integer.parseInt(var.substring(idx+1));
	 var = var.substring(0,idx);
       }
      if (var.equals(id)) {
	 if (rslt == null) {
	    rslt = s;
	    bestline = vln;
	  }
	 else if (vln > 0 && vln > bestline && vln <= lno) {
	    rslt = s;
	    bestline = vln;
	  }
       }
    }
   return rslt;
}



/********************************************************************************/
/*										*/
/*	Location methods							*/
/*										*/
/********************************************************************************/

void getExecutedLocations(Set<String> rslt)
{
   if (getFile() == null) return;

   String file = getFile().getPath();

   DiexecuteVarVal vv = getLineNumbers();
   for (Integer iv : vv.getLineNumbers()) {
      String key = file + "@" + iv;
      rslt.add(key);
    }

   for (DiexecuteCall vc : getInnerCalls()) {
      vc.getExecutedLocations(rslt);
    }
}


/********************************************************************************/
/*										*/
/*	Output methods								*/
/*										*/
/********************************************************************************/

JSONObject getJsonExecTrace(boolean inner)
{
   JSONObject rslt = new JSONObject();
   rslt.put("ID",getContextId());
   rslt.put("METHOD",getMethod());
// rslt.put("FILE",getFile());
   rslt.put("START_TIME",getStartTime());
   rslt.put("END_TIME",getEndTime());
   
   if (getParentCall() == null) {
      if (!for_trace.isReturn()) {
	 String exc = for_trace.getExceptionType();
	 if (exc != null) rslt.put("EXCEPTION",exc);
       }
    }
   else {
      for (Element e : IvyXml.children(context_element,"VARIABLE")) {
	 String nm = IvyXml.getAttrString(e,"NAME");
	 if (nm.equals("*THROWS*")) {
	    DiexecuteVarVal vv = new DiexecuteVarVal(e,null);
	    String exc = vv.getDataType(getEndTime());
	    rslt.put("EXCEPTION",exc);
	    break;
	  }
       }
    }
   
   if (!inner) {
      JSONArray vars = new JSONArray();
      for (Element e : IvyXml.children(context_element,"VARIABLE")) {
         String nm = IvyXml.getAttrString(e,"NAME");
         if (nm.startsWith("*")) continue;
         JSONObject var = new JSONObject();
         var.put("NAME",nm);
         var.put("DEFINITION_LINE",IvyXml.getAttrInt(e,"LINE"));
         vars.put(var);
       }
      rslt.put("VARIABLES",vars);
      
      JSONArray calls = new JSONArray();
      for (DiexecuteCall c : getInnerCalls()) {
         JSONObject co = c.getJsonExecTrace(true);
         calls.put(co);
       }
      rslt.put("CALLS",calls);
    }
   
   return rslt;
}


JSONArray getJsonLineTrace()
{
   JSONArray rslt = new JSONArray();

   DiexecuteVarVal lines = getLineNumbers();
   if (lines != null) {
      int prev = -1;
      long start = 0;
      for (Long t : lines.getTimeChanges()) {
	 int lv = lines.getLineValue(t);
	 if (prev > 0) {
	    JSONObject lobj = buildLineObject(prev,start,t-1);
	    rslt.put(lobj);
	  }
	 prev = lv;
	 start = t;
       }
      if (prev >= 0) {
	 JSONObject lobj = buildLineObject(prev,start,getEndTime());
	 rslt.put(lobj);
       }
    }

   return rslt;
}



private JSONObject buildLineObject(int lno,long start,long end)
{
   JSONObject lobj = new JSONObject();

   lobj.put("LINE",lno);
   lobj.put("START_TIME",start);
   lobj.put("END_TIME",end);

   return lobj;
}


JSONObject getJsonVarTrace(String varname)
{
   JSONObject rslt = new JSONObject();

   DiexecuteVarVal var = null;
   if (!varname.contains("(")) {
      // ensure we aren't being passed a call
      var = getTraceVarValueFlex(varname,getEndTime());
    }
   if (var == null) {
      rslt.put("ERROR","Variable " + varname + "not found");
      return rslt;
    }

   DiexecuteVarVal linv = getLineNumbers();

   Set<String> done = new HashSet<>();
   rslt.put("NAME",var.getName());
   JSONArray vals = new JSONArray();
   for (Long t : var.getTimeChanges()) {
      DiexecuteVarVal val = var.getValueAtTime(for_trace,t);
      int lno = linv.getLineValue(t);
      JSONObject top = new JSONObject();
      top.put("AT_TIME",t);
      top.put("AT_LINE",lno);
      top.put("VALUE",val.toJsonValue(for_trace,t,done));
      vals.put(top);
    }
   rslt.put("VALUES",vals);

   return rslt;
}





}	// end of class DiexecuteCall




/* end of DiexecuteCall.java */

