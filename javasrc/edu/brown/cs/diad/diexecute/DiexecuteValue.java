/********************************************************************************/
/*                                                                              */
/*              DiexecuteValuie.java                                            */
/*                                                                              */
/*      Representation of a value (that can change over time) in an execution   */
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

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceValue;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteValue implements DiexecuteConstants, DiadTraceValue
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Element         value_element;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteValue(Element v)
{
   value_element = v;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public long getStartTime()                     
{
   return IvyXml.getAttrLong(value_element,"TIME");
}

@Override public boolean isNull()
{
   return IvyXml.getAttrBool(value_element,"NULL");
}


@Override public String getDataType()
{
   return IvyXml.getAttrString(value_element,"TYPE");
}


@Override public Long getNumericValue()
{
   try {
      return Long.parseLong(IvyXml.getText(value_element));
    }
   catch (NumberFormatException e) { }
   
   return null;
}


@Override public int getLineValue()
{
   try {
      return Integer.parseInt(IvyXml.getText(value_element));
    }
   catch (NumberFormatException e) { }
   
   return 0;
}


@Override public String getValue()
{
   if (IvyXml.getAttrBool(value_element,"NULL")) return null;
   else if (IvyXml.getAttrBool(value_element,"OBJECT")) {
      String cls = IvyXml.getAttrString(value_element,"CLASS");
      if (cls != null) return cls;
      String fil = IvyXml.getAttrString(value_element,"FILE");
      if (fil != null) return fil;
      // getting more detailed info requires handling REFS
      return "{" + getDataType() + "}";
    }
   else if (IvyXml.getAttrBool(value_element,"ARRAY")) {
      // getting more detailed info requires handling REFS
      return "[" + getDataType() + "]";
    }
   else if (IvyXml.getAttrBool(value_element,"CHARS")) {
      int len = IvyXml.getAttrInt(value_element,"LENGTH");
      String txt = IvyXml.getText(value_element);
      return IvyXml.decodeCharacters(txt,len);
    }
   
   return IvyXml.getText(value_element);
}


@Override public String getEnum()
{
   return IvyXml.getAttrString(value_element,"ENUM");
}


@Override public DiexecuteValue getFieldValue(DiadTrace rvtr,String fld,long when)
{
   DiexecuteTrace vtr = (DiexecuteTrace) rvtr;
   Element use = null;
   
   for (Element flde : IvyXml.children(value_element,"FIELD")) {
      String nm = IvyXml.getAttrString(flde,"NAME");
      if (nm.equals(fld)) {
         use = flde;
         break;
       }
      else if (nm.endsWith("." + fld)) use = flde;
    }
   
   if (use != null) {
      DiexecuteVariable vvar = new DiexecuteVariable(use); 
      return vvar.getValueAtTime(vtr,when); 
    }
   
   return null;
}



@Override public DiexecuteValue getIndexValue(DiadTrace rvtr,int idx,long when)
{
   DiexecuteTrace vtr = (DiexecuteTrace) rvtr;
   Element use = null;
   for (Element flde : IvyXml.children(value_element,"ELEMENT")) {
      int eidx = IvyXml.getAttrInt(flde,"INDEX");
      if (eidx == idx) {
         use = flde;
         break;
       }
    }
   
   if (use != null) {
      DiexecuteVariable vvar = new DiexecuteVariable(use);
      return vvar.getValueAtTime(vtr,when);
    }
   
   return null;
}


@Override public String getId()
{
   return IvyXml.getAttrString(value_element,"ID");
}


@Override public int getArrayLength()
{
   return IvyXml.getAttrInt(value_element,"SIZE");
}



/********************************************************************************/
/*                                                                              */
/*      Equality methods                                                        */
/*                                                                              */
/********************************************************************************/

@Override public boolean equals(Object o) 
{
   if (o instanceof DiexecuteValue) {
      DiexecuteValue vv = (DiexecuteValue) o;
      return value_element.equals(vv.value_element);
    }
   return false;
}


@Override public int hashCode()
{
   return value_element.hashCode();
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String toString() 
{
   return IvyXml.convertXmlToString(value_element);
}


JSONObject toJson(DiexecuteTrace trace,DiexecuteVariable linv)
{
   JSONObject rslt = new JSONObject();
   rslt.put("SET_TIME",getStartTime());
   rslt.put("SET_LINE",linv.getLineAtTime(getStartTime()));
   rslt.put("TYPE",getDataType());
   if (IvyXml.getAttrBool(value_element,"OBJECT")) {
      JSONArray flds = new JSONArray();
      rslt.put("FIELDS",flds);
      for (Element flde : IvyXml.children(value_element,"FIELD")) {
         DiexecuteVariable fvar = new DiexecuteVariable(flde);
         JSONObject fld = fvar.toJson(trace,linv);
         flds.put(fld);
       }
    }
   else if (IvyXml.getAttrBool(value_element,"ARRAY")) {
      rslt.put("LENGTH",getArrayLength());
      JSONArray flds = new JSONArray();
      rslt.put("ELEMENTS",flds);
      for (Element flde : IvyXml.children(value_element,"ELEMENT")) {
         DiexecuteVariable fvar = new DiexecuteVariable(flde);
         JSONObject fld = fvar.toJson(trace,linv);
         flds.put(fld);
       }
    }
   else {
      rslt.put("VALUE",getValue());
    }
   
   return rslt;
}



}       // end of class DiexecuteValuie




/* end of DiexecuteValuie.java */

