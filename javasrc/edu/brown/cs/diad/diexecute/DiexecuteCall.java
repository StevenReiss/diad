/********************************************************************************/
/*                                                                              */
/*              DiexecuteCall.java                                              */
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

import java.io.File;
import java.util.ArrayList;
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
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteCall implements DiadTraceCall
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Element         context_element;
private DiexecuteTrace  for_trace;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteCall(DiexecuteTrace vt,Element ctx)
{
   for_trace = vt;
   context_element = ctx;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
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

int getContextId()
{
   return IvyXml.getAttrInt(context_element,"ID");
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



DiexecuteVarVal getValueAtTime(DiadTrace trace,String name,long when) 
{
   int idx = name.lastIndexOf("?");
   if (idx < 0) {
      DiexecuteVarVal var = getTraceVariable(name);
      return var.getValueAtTime(trace,when);  
    }
   String pre = name.substring(0,idx);
   String sub = name.substring(idx+1);
   DiexecuteVarVal var = getValueAtTime(trace,pre,when);
   DiexecuteVarVal val1 = (DiexecuteVarVal) var.getChild(sub,when);
   
   return val1;
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
/*                                                                              */
/*      Location methods                                                        */
/*                                                                              */
/********************************************************************************/

void getExecutedLocations(Set<String> rslt)
{
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
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

JSONObject getJsonExecTrace()
{
   JSONObject rslt = new JSONObject();
   rslt.put("ID",getContextId());
   rslt.put("METHOD",getMethod());
   rslt.put("FILE",getFile());
   rslt.put("START_TIME",getStartTime());
   rslt.put("END_TIME",getEndTime());
   JSONArray calls = new JSONArray();
   for (DiexecuteCall c : getInnerCalls()) {
      JSONObject co = c.getJsonExecTrace();
      calls.put(co);
    }
   rslt.put("CALLS",calls);
   
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
   DiexecuteVarVal var = getTraceVariable(varname);
   if (var == null) return null;
   
   DiexecuteVarVal linv = getLineNumbers();
   
   JSONObject rslt = new JSONObject();
   rslt.put("NAME",var.getName());
   JSONArray vals = new JSONArray();
   for (Long t : var.getTimeChanges()) {
      DiexecuteVarVal val = var.getValueAtTime(for_trace,t);
      JSONObject va = val.toJson(for_trace,linv);  
      if (va != null) vals.put(va);
    }
   rslt.put("VALUES",vals);
   
   return rslt;
}





}       // end of class DiexecuteCall




/* end of DiexecuteCall.java */

