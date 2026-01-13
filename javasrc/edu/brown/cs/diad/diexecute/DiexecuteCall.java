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

import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceCall;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVariable;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteCall implements DiadTraceCall
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Element         context_element;
private DiexecuteTrace   for_trace;



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



@Override public DiexecuteVariable getLineNumbers()
{
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals("*LINE*")) return new DiexecuteVariable(e);
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


Map<String,DiexecuteVariable> getVariables()
{
   Map<String,DiexecuteVariable> rslt = new LinkedHashMap<>();
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals("*LINE*")) continue;
      rslt.put(nm,new DiexecuteVariable(e));
    }
   return rslt;
}


@Override public Map<String,DiadTraceVariable> getTraceVariables()
{
   Map<String,DiadTraceVariable> rslt = new LinkedHashMap<>();
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals("*LINE*")) continue;
      rslt.put(nm,new DiexecuteVariable(e));
    }
   return rslt;
}


DiexecuteVariable getTraceVariable(String name)
{
   for (Element e : IvyXml.children(context_element,"VARIABLE")) {
      String nm = IvyXml.getAttrString(e,"NAME");
      if (nm.equals(name)) return new DiexecuteVariable(e);
    }
   
   return null;
}



/********************************************************************************/
/*                                                                              */
/*      Location methods                                                        */
/*                                                                              */
/********************************************************************************/

void getExecutedLocations(Set<String> rslt)
{
   DiexecuteVariable vv = getLineNumbers();
   String file = getFile().getPath();
   for (DiexecuteValue v : vv.getValues(null)) {
      int ln = v.getLineValue();
      String key = file + "@" + ln;
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
   
   DiexecuteVariable lines = getLineNumbers();
   if (lines != null) {
      DiexecuteValue prev = null;
      for (DiexecuteValue val : lines.getValues(for_trace)) {
         if (prev != null) {
            JSONObject lobj = buildLineObject(prev,val.getStartTime()-1);
            rslt.put(lobj);
          }
         prev = val;
       }
      if (prev != null) {
         JSONObject lobj = buildLineObject(prev,getEndTime());
         rslt.put(lobj);
       }
    }
   
   return rslt;
}



private JSONObject buildLineObject(DiexecuteValue val,long end)
{
   JSONObject lobj = new JSONObject();
   
   int lno = val.getLineValue();
   lobj.put("LINE",lno);
   lobj.put("START_TIME",val.getStartTime());
   lobj.put("END_TIME",end);
   
   return lobj;
}


JSONObject getJsonVarTrace(String varname)
{
   DiexecuteVariable var = getTraceVariable(varname);
   if (var == null) return null;
   
   DiexecuteVariable linv = getLineNumbers();
   
   JSONObject rslt = new JSONObject();
   rslt.put("NAME",var.getName());
   JSONArray vals = new JSONArray();
   for (DiexecuteValue val : var.getValues(for_trace)) {
      JSONObject va = val.toJson(for_trace,linv);  
      if (va != null) vals.put(va);
    }
   rslt.put("VALUES",vals);
   
   return rslt;
}





}       // end of class DiexecuteCall




/* end of DiexecuteCall.java */

