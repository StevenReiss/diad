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




}       // end of class DiexecuteCall




/* end of DiexecuteCall.java */

