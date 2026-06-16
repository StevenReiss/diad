/********************************************************************************/
/*                                                                              */
/*              DiexecuteVarVal.java                                            */
/*                                                                              */
/*      Value that may change over time                                         */
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;

class DiexecuteVarVal implements DiexecuteConstants, DiadTraceVarVal
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiexecuteVarVal par_varval;
private Element        var_element;
private String         full_name;


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteVarVal(Element v,DiexecuteVarVal par)
{
   var_element = v;
   par_varval = par;
   full_name = null;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String getName()
{
   String nm = IvyXml.getAttrString(var_element,"NAME");
   if (nm == null) {
      String idx = IvyXml.getAttrString(var_element,"INDEX");
      if (idx != null) {
         nm = "[" + idx + "]";
       }
    }
   if (nm == null && par_varval != null) nm = par_varval.getName();
   
   return nm;    
}


@Override public String getFullName()
{
   if (full_name == null) {
      full_name = getName();
      if (full_name == null) return null;
      if (par_varval != null) {
         String pfx = par_varval.getFullName();
         if (pfx != null && !pfx.isEmpty()) {
            full_name = pfx + "?" + full_name;
          }
       }
    }
   
   return full_name;
}



@Override public boolean hasChildren(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   for (Element e : IvyXml.children(val.var_element)) {
      if (IvyXml.isElement(e,"FIELD") ||
            IvyXml.isElement(e,"ELEMENT")) {
         return true;
       }
    }
   return false;
}


@Override public Collection<String> getChildNames(long when)
{
   List<String> rslt = new ArrayList<>();
   
   DiexecuteVarVal val = getValueAtTime(when);
   for (Element e : IvyXml.children(val.var_element)) {
      if (IvyXml.isElement(e,"FIELD")) {
         rslt.add(IvyXml.getAttrString(e,"NAME"));
       }
      else if (IvyXml.isElement(e,"ELEMENT")) {
         rslt.add("[" + IvyXml.getAttrInt(e,"INDEX") + "]");
       }
    }
   
   return rslt;
}

@Override public DiexecuteVarVal getChild(String name,long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   IvyLog.logD("DIEXECUTE","Get child " + name + " " + when + " " + 
         val.getFullName());
   
   for (Element e : IvyXml.children(val.var_element)) {
      String key = null;
      String key1 = null;
      if (IvyXml.isElement(e,"FIELD")) {
         key = IvyXml.getAttrString(e,"NAME");
         int idx = key.lastIndexOf(".");
         if (idx > 0) key1 = key.substring(idx+1);
         else key1 = key;
       }
      else if (IvyXml.isElement(e,"ELEMENT")) {
         key = IvyXml.getAttrString(e,"INDEX");
         key1 = "[" + key + "]";
       }
      if (key == null || key1 == null) {
         IvyLog.logI("DIEXECUTE","Unknown child of varval: " + name + " " +
               val.getChildNames(when) + " " +
               IvyXml.convertXmlToString(e));
       }
      if (key != null && (key.equals(name) || key1.equals(name))) {
         DiexecuteVarVal vv0 = new DiexecuteVarVal(e,this);
         vv0 = vv0.getValueAtTime(when);
         return vv0;
       }
    }
   
   // Handle bad specifications from the LLM
   
   // First, check for [#] with a List or Vector
   String typ = IvyXml.getAttrString(val.var_element,"TYPE");
   if (name.startsWith("[")) {
      if (typ != null) {
         String collfld = null;
         switch (typ) {
            case "java.util.Vector" :
            case "java.util.Stack" :
               collfld = "elementData";
               break;
          }
         if (collfld != null) {
            for (Element fe : IvyXml.children(val.var_element,"FIELD")) {
               String fn = IvyXml.getAttrString(fe,"NAME");
               int idx = fn.lastIndexOf(".");
               if (idx > 0) fn = fn.substring(idx+1);
               if (fn.equals(collfld)) {
                  DiexecuteVarVal vv = new DiexecuteVarVal(fe,this);
                  vv = vv.getValueAtTime(when);
                  DiexecuteVarVal vv0 = vv.getChild(name,when);
                  if (vv0 != null) return vv0;
                }
             }
          }
       }
    }
   
   // next check for missing intermediate element
   for (Element fe : IvyXml.children(val.var_element)) {
      if (IvyXml.isElement(fe,"FIELD")) {
         DiexecuteVarVal vv1 = new DiexecuteVarVal(fe,this);
         DiexecuteVarVal vv2 = vv1.getChild(name,when);
         if (vv2 != null) return vv2;
       }
      else if (IvyXml.isElement(fe,"ELEMENT")) {
         int idx = IvyXml.getAttrInt(fe,"INDEX");
         if (idx == 0) {
            DiexecuteVarVal vv1 = new DiexecuteVarVal(fe,this);
            DiexecuteVarVal vv2 = vv1.getChild(name,when);
            if (vv2 != null) return vv2;
          }
       }
    }
   
   return null;
}


@Override public String getDataType(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   return IvyXml.getAttrString(val.var_element,"TYPE");
}

@Override public List<Long> getTimeChanges()
{
   List<Long> rslt = new ArrayList<>();
   for (Element e : IvyXml.children(var_element,"VALUE")) {
      long etime = IvyXml.getAttrLong(e,"TIME");
      if (etime >= 0) rslt.add(etime);
    }
   
   // should also take into account time changes for subeleemnts
   
   if (IvyXml.isElement(var_element,"VALUE")) {
      long vtime = IvyXml.getAttrLong(var_element,"TIME");
      if (vtime > 0) rslt.add(vtime); 
    }
   
   if (rslt.isEmpty()) return Collections.singletonList(0L);
   
   return rslt; 
}


Collection<Long> getAllTimeChanges(DiexecuteTrace trace)
{
   Set<Long> rslt = new TreeSet<>();
   addAllTimeChanges(var_element,rslt);
   
   DiexecuteCall pctx = null;
   int pline = 0;
   for (Iterator<Long> it = rslt.iterator(); it.hasNext(); ) {
      Long time = it.next();
      DiexecuteCall ctx = trace.getContextForTime(time);
      if (ctx == pctx) {
         DiexecuteVarVal lins = ctx.getLineNumbers();
         if (lins != null) {
            int lin = lins.getLineValue(time);
            if (lin == pline) it.remove();
            else pline = lin;
          }
       }
      else {
         pline = 0;
       }
      pctx = ctx;
    }
   
   return rslt;
}


private void addAllTimeChanges(Element xml,Set<Long> rslt)
{
   if (IvyXml.isElement(xml,"VALUE")) {
      long vtime = IvyXml.getAttrLong(xml,"TIME");
      if (vtime > 0) rslt.add(vtime);
    }
   for (Element sub : IvyXml.elementsByTag(xml,"VALUE")) {
      addAllTimeChanges(sub,rslt);
    }
}

@Override public long getUpdateTime(long when)
{
   if (when <= 0) return -1;
   long last = 0;
   for (Element e : IvyXml.children(var_element,"VALUE")) {
      long etime = IvyXml.getAttrLong(e,"TIME");
      if (etime >= 0 && etime <= when) {
         last = etime;
       }
    }
   return last;
}


@Override public boolean isNull(long when) 
{
   DiexecuteVarVal val = getValueAtTime(when);
   
   return IvyXml.getAttrBool(val.var_element,"NULL");
}


@Override public long getStartTime()
{
   long time = IvyXml.getAttrLong(var_element,"TIME");
   if (time >= 0) return time;
   if (par_varval != null) {
      return par_varval.getStartTime();
    } 
   
   return 0;
}


@Override public int getLineValue(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   
   try {
      return Integer.parseInt(IvyXml.getText(val.var_element));
    }
   catch (NumberFormatException e) { }
   
   return 0;
}

@Override public Long getNumericValue(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   
   try {
      return Long.parseLong(IvyXml.getText(val.var_element));
    }
   catch (NumberFormatException e) { }
   
   return null;
}

@Override public String getStringValue(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   
   if (IvyXml.getAttrBool(val.var_element,"NULL")) return null;
   else if (IvyXml.getAttrBool(val.var_element,"OBJECT")) {
      String cls = IvyXml.getAttrString(val.var_element,"CLASS");
      if (cls != null) return cls;
      String fil = IvyXml.getAttrString(val.var_element,"FILE");
      if (fil != null) return fil;
      // getting more detailed info requires handling REFS
      return "{" + getDataType(when) + "}";
    }
   else if (IvyXml.getAttrBool(val.var_element,"ARRAY")) {
      // getting more detailed info requires handling REFS
      return "[" + getDataType(when) + "]";
    }
   else if (IvyXml.getAttrBool(val.var_element,"CHARS")) {
      int len = IvyXml.getAttrInt(val.var_element,"LENGTH");
      String txt = IvyXml.getText(val.var_element);
      return IvyXml.decodeCharacters(txt,len);
    }
   
   return IvyXml.getText(val.var_element);
}


@Override public String getId(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   
   return IvyXml.getAttrString(val.var_element,"ID");
}


@Override public int getArrayLength(long when)
{
   DiexecuteVarVal val = getValueAtTime(when);
   
   return IvyXml.getAttrInt(val.var_element,"SIZE");
}


@Override public List<Integer> getLineNumbers() 
{
   List<Integer> rslt = new ArrayList<>();
   
   for (Element e : IvyXml.children(var_element,"VALUE")) {
      int ln = Integer.parseInt(IvyXml.getText(e));
      rslt.add(ln);
    }
   
   return rslt;
}

/********************************************************************************/
/*                                                                              */
/*      Helper methods                                                          */
/*                                                                              */
/********************************************************************************/

DiexecuteVarVal getValueAtTime(long when)
{
   Element fnd = null;
   for (Element e : IvyXml.children(var_element,"VALUE")) {
      long etime = IvyXml.getAttrLong(e,"TIME");
      if (etime <= when) fnd = e;
    }
   if (fnd == null) return this;
   
   return new DiexecuteVarVal(fnd,this);
}
 

public DiadTraceVarVal getValueAt(DiadTrace trace,long when)
{
   return getValueAtTime(trace,when);
}


DiexecuteVarVal getValueAtTime(DiadTrace trace0,long when)
{
   DiexecuteTrace trace = (DiexecuteTrace) trace0;
   
   Element fnd = null;
   for (Element e : IvyXml.children(var_element,"VALUE")) {
      long etime = IvyXml.getAttrLong(e,"TIME");
      if (etime <= when) fnd = e;
    }
   if (fnd == null) {
      return dereference(trace0);
    }
   else if (trace != null) {
      fnd = trace.dereference(fnd);
    }
   
   return new DiexecuteVarVal(fnd,this);
}



DiexecuteVarVal dereference(DiadTrace trace0)
{
   if (trace0 == null) return this;
   DiexecuteTrace trace = (DiexecuteTrace) trace0;
   Element e = trace.dereference(var_element);
   if (e == var_element) return this;
   return new DiexecuteVarVal(e,par_varval);
}


/********************************************************************************/
/*                                                                              */
/*      Output Methods                                                          */
/*                                                                              */
/********************************************************************************/

Object toJsonValue(DiadTrace trace,long t,Set<String> done)
{
   if (done == null) done = new HashSet<>();
   DiexecuteVarVal vv0 = getValueAtTime(trace,t);
   if (vv0 != this) {
      return vv0.toJsonValue(trace,t,done);
    }
   
   String id = IvyXml.getAttrString(var_element,"ID");
   String typ = IvyXml.getAttrString(var_element,"TYPE");
   if (id != null) {
      if (!done.add(id)) {
         JSONObject jo = new JSONObject();
         jo.put("REF",id);
         return jo;
       }
    }
   
   if (typ != null) {
      String collfld = null;
      switch (typ) {
         case "java.util.Vector" :
         case "java.util.Stack" :
            collfld = "elementData";
            break;
       }
      if (collfld != null) {
         for (Element fe : IvyXml.children(var_element,"FIELD")) {
            String fn = IvyXml.getAttrString(fe,"NAME");
            int idx = fn.lastIndexOf(".");
            if (idx > 0) fn = fn.substring(idx+1);
            if (fn.equals(collfld)) {
               DiexecuteVarVal vv = new DiexecuteVarVal(fe,this);
               vv = vv.getValueAtTime(trace,t);
               return vv.toJsonValue(trace,t,done);
             }
          }
       }
    }
   
   if (IvyXml.getAttrBool(var_element,"NULL")) {
      return JSONObject.NULL;
    }
   else if (IvyXml.getAttrBool(var_element,"OBJECT")) {
      JSONObject jo = new JSONObject();
      jo.put("ID",id);
      for (Element fe : IvyXml.children(var_element,"FIELD")) {
         String fn = IvyXml.getAttrString(fe,"NAME");
         int idx = fn.lastIndexOf(".");
         if (idx > 0) fn = fn.substring(idx+1);
         DiexecuteVarVal vv = new DiexecuteVarVal(fe,this);
         vv = vv.getValueAtTime(trace,t);
         jo.put(fn,vv.toJsonValue(trace,t,done));
       }
      return jo;
    }
   else if (IvyXml.getAttrBool(var_element,"ARRAY")) {
      JSONArray arr = new JSONArray();
      int sz = IvyXml.getAttrInt(var_element,"SIZE");
      for (Element e : IvyXml.children(var_element,"ELEMENT")) {
         DiexecuteVarVal ve = new DiexecuteVarVal(e,this);
         ve = ve.getValueAtTime(t);
         int idx = IvyXml.getAttrInt(e,"INDEX");
         if (idx >= 0) {
            arr.put(idx,ve.toJsonValue(trace,t,done));
          }
         else {
            if (IvyXml.getAttrBool(e,"DEFAULT")) {
               Object dfltelt = ve.toJsonValue(trace,t,done);
               for (int i = 0; i < sz; ++i) {
                  if (arr.opt(i) == null) {
                     arr.put(i,dfltelt);
                   }
                }
             }
          }
       }
      return arr;
    }
   else if (IvyXml.getAttrBool(var_element,"CHARS")) {
      return getStringValue(t);
    }
   else {
      if (typ == null) {
         typ = "?";
       }
      switch (typ) {
         case "int" :
         case "short" :
         case "byte" :
         case "long" :
         case "java.lang.Integer" :
         case "java.lang.Short" :
         case "java.lang.Byte" :
         case "java.lang.Long" :
            return Long.parseLong(IvyXml.getText(var_element));
         case "float" :
         case "double" :
         case "java.lang.Float" :
         case "java.lang.Double" :
            return Double.parseDouble(IvyXml.getText(var_element));
         case "boolean" :
         case "java.lang.Boolean" :
            String s0 = IvyXml.getText(var_element);
            boolean fg = false;
            if (s0 != null && !s0.isEmpty()) {
               fg = "1YyTt".indexOf(s0.charAt(0)) >= 0;
             }
            return fg;
         case "char" :
         case "java.lang.Character" :
            return Long.parseLong(IvyXml.getText(var_element));
         default :
         case "java.lang.String" :
            return IvyXml.getText(var_element);
       }
    }
}


@Override public String toString()
{
   return "VAR: " + IvyXml.convertXmlToString(var_element);
}


}       // end of class DiexecuteVarVal




/* end of DiexecuteVarVal.java */

