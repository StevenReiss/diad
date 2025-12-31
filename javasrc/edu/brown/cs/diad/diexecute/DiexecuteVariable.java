/********************************************************************************/
/*                                                                              */
/*              DiexecuteVariable.java                                          */
/*                                                                              */
/*      Representation of an execution variable                                 */
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

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVariable;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceValue;
import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteVariable implements DiexecuteConstants, DiadTraceVariable
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Element         variable_element;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteVariable(Element v)
{
   variable_element = v;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String getName()                       
{
   return IvyXml.getAttrString(variable_element,"NAME");
}

List<DiexecuteValue> getValues(DiexecuteTrace trace)
{
   List<DiexecuteValue> rslt = new ArrayList<>();
   for (Element e : IvyXml.children(variable_element,"VALUE")) {
      Element v1 = e;
      if (trace != null) v1 = trace.dereference(e);
      rslt.add(new DiexecuteValue(v1));
    }
   
   return rslt;
}


@Override public List<DiadTraceValue> getTraceValues(DiadTrace rtrace)
{
   DiexecuteTrace trace = (DiexecuteTrace) rtrace;
   List<DiadTraceValue> rslt = new ArrayList<>();
   for (Element e : IvyXml.children(variable_element,"VALUE")) {
      Element v1 = e;
      if (trace != null) v1 = trace.dereference(e);
      rslt.add(new DiexecuteValue(v1));
    }
   
   return rslt;
}




@Override public DiexecuteValue getValueAtTime(DiadTrace rtrace,long time)
{
   DiexecuteTrace trace = (DiexecuteTrace) rtrace;
   Element prior = null;
   for (Element e : IvyXml.children(variable_element,"VALUE")) {
      long t0 = IvyXml.getAttrLong(e,"TIME");
      if (t0 > 0 && t0 > time) break;
      if (trace == null) prior = e;
      else prior = trace.dereference(e);
    }
   
   return new DiexecuteValue(prior);
}


@Override public int getLineAtTime(long time) 
{
   DiexecuteValue vv = getValueAtTime(null,time);
   if (vv == null) return 0;
   Long lv = vv.getNumericValue();
   if (lv == null) return 0;
   return lv.intValue();
}


/********************************************************************************/
/*                                                                              */
/*      Equality methods                                                        */
/*                                                                              */
/********************************************************************************/

@Override public boolean equals(Object o) 
{
   if (o instanceof DiexecuteVariable) {
      DiexecuteVariable vv = (DiexecuteVariable) o;
      return variable_element.equals(vv.variable_element);
    }
   return false;
}


@Override public int hashCode()
{
   return variable_element.hashCode();
}



}       // end of class DiexecuteVariable




/* end of DiexecuteVariable.java */

