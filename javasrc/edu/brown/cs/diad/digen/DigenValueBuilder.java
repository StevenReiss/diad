/********************************************************************************/
/*                                                                              */
/*              DigenValueBuilder.java                                          */
/*                                                                              */
/*      Create code to construct a value or call                                */
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



package edu.brown.cs.diad.digen;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadCandidate;
import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.jcomp.JcompType;
import edu.brown.cs.ivy.jcomp.JcompTyper;
import edu.brown.cs.ivy.xml.IvyXml;

class DigenValueBuilder implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DigenTestCreator test_creator;
private long            start_time;
private JcompTyper       jcomp_typer;
private JcompType       collection_type;
private JcompType       map_type;
private DigenValueContext cur_context;
private DiadTrace       for_trace;

private Set<DiadTraceVarVal>    done_values;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenValueBuilder(DigenTestCreator mgr,DiadTrace trace,long start,JcompTyper typer)
{
   test_creator = mgr;
   for_trace = trace;
   start_time = start;
   jcomp_typer = typer;
   collection_type = typer.findSystemType("java.util.Collection");
   map_type = typer.findSystemType("java.util.Map");
   done_values = new HashSet<>();
   cur_context = new DigenValueContext();
}   



void finished()
{ 
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

JcompTyper getJcompTyper()                      { return jcomp_typer; }


DigenValueContext getInitializationContext()
{
   return cur_context;
}


DigenCodeFragment getInitializations()
{
   return cur_context.getInitializations(); 
}

long getStartTime()                             { return start_time; }


/********************************************************************************/
/*                                                                              */
/*      Add item to evaluate                                                    */
/*                                                                              */
/********************************************************************************/

DigenCodeFragment computeValue(DiadTraceVarVal var)
{
   if (var == null) return null;
   DiadTraceVarVal val = var.getValueAt(for_trace,start_time); 
   
   IvyLog.logD("DIGEN","Work on variable " + var.getFullName());
   
   DigenCodeFragment rslt = cur_context.getComputedValue(val);
   if (rslt != null) return rslt;
   
   if (!done_values.add(val)) {
      // need to handle recursion here
      return null;
    }
   
   IvyLog.logD("DIGEN","Compute value " + val);
   
   DigenCodeFragment pcf = buildSimpleValue(val);
   if (pcf == null) {
      pcf = buildComplexValue(val);
    }
   
   if (pcf == null && cur_context != null) {
      cur_context.addInitialization("// Can't build value for " + var + "\n");
    }
  
   return pcf;
}


/********************************************************************************/
/*                                                                              */
/*      Queue value to work on later                                            */
/*                                                                              */
/********************************************************************************/

private DigenCodeFragment buildComplexValue(DiadTraceVarVal val)
{
   long qtime = start_time;
   String typ = val.getDataType(qtime);
   JcompType jtyp = jcomp_typer.findType(typ);
   DigenCodeFragment rslt = null;
   boolean issimple = false;
   
   IvyLog.logD("DIGEN","Build complex value " + typ + " " + qtime + " " + val);
   
   if (jtyp.isArrayType()) {
      int ct = val.getArrayLength(for_trace,qtime);
      JcompType btyp = jtyp.getBaseType();
      rslt = new DigenCodeFragment("new " + btyp + "[" + ct + "] {\n");
      for (int i = 0; i < ct; ++i) {
         DiadTraceVarVal ftv1 = getIndexValue(val,i);
         DigenCodeFragment cfg = computeValue(ftv1);
         if (cfg == null) cfg = new DigenCodeFragment("null");
         rslt = rslt.append(cfg + ",",true);
       }
      rslt = rslt.append("}");
    }
   else if (jtyp.isCompatibleWith(collection_type)) {
      List<DiadTraceVarVal> elts = val.getElements(for_trace,start_time);
      if (elts != null) {
         DigenCodeFragment cfg1 = new DigenCodeFragment("new " + jtyp.getName() + "()");
         rslt = cur_context.saveComputedValue(val,cfg1);
         issimple = true;
         for (DiadTraceVarVal elt : elts) {
            DiadTraceVarVal etv = elt.getValueAt(for_trace,start_time);
            DigenCodeFragment cfg2 = computeValue(etv);
            if (cfg2 != null) {
               String init = rslt.getCode() + ".add(" + cfg2.getCode() + ");";
               cur_context.addInitialization(init); 
             }
          } 
       }
    }
   else if (jtyp.isCompatibleWith(map_type)) {
      List<DiadTraceVarVal> elts = val.getElements(for_trace,start_time);
      if (elts != null) {
         DigenCodeFragment cfg1 = new DigenCodeFragment("new " + jtyp.getName() + "()");
         rslt = cur_context.saveComputedValue(val,cfg1);
         for (DiadTraceVarVal etv : elts) {
            DiadTraceVarVal key = getFieldValue(etv,"key");
            DigenCodeFragment keyf = computeValue(key);
            DiadTraceVarVal val2 = getFieldValue(etv,"value");
            DigenCodeFragment valf = computeValue(val2);
            if (valf != null) {
               String init = rslt.getCode() + ".put(" + keyf.getCode() + "," +
                  valf.getCode() + ");";
               cur_context.addInitialization(init);
             }
          } 
       }
    }
   else {
      Map<String,JcompType> flds = jtyp.getFields(jcomp_typer);
      Map<String,DigenCodeFragment> values = new HashMap<>();
      for (String fld : flds.keySet()) {
         String fnm = fld;
         int idx = fnm.lastIndexOf(".");
         if (idx > 0) fnm = fld.substring(idx+1);
         if (fnm.equals("this") || fnm.startsWith("this$")) continue;
         
         JcompSymbol fldsym = jtyp.lookupField(jcomp_typer,fnm);
         if (fldsym == null) {
            IvyLog.logE("DIGEN","Can't find field " + fld);
            continue;
          }
         
         DiadTraceVarVal ftv1 = getFieldValue(val,fld);
         IvyLog.logD("DIGEN","Work on field " + fld + " " + ftv1);
         DigenCodeFragment fldf = computeValue(ftv1);
         if (fldf != null) values.put(fld,fldf);
       }
      rslt = askForCode(jtyp.getName(),values);
      issimple = true;
    }
   
   if (!issimple) {
      rslt = cur_context.saveComputedValue(val,rslt);
    }
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Variable access methods                                                 */
/*                                                                              */
/********************************************************************************/

DiadTraceVarVal getIndexValue(DiadTraceVarVal val,int idx)
{
   DiadTraceVarVal val1 = val.getChild("[" + idx + "]",for_trace,start_time);
   if (val1 == null) return null;
   
   return val1.getValueAt(for_trace,start_time);
}


DiadTraceVarVal getFieldValue(DiadTraceVarVal val,String fld)
{
   DiadTraceVarVal val1 = val.getChild(fld,for_trace,start_time);
   if (val1 == null) return null;
   return val1.getValueAt(for_trace,start_time);
}


/********************************************************************************/
/*                                                                              */
/*      Build a simple value                                                    */
/*                                                                              */
/********************************************************************************/

DigenCodeFragment buildSimpleValue(DiadTraceVarVal var)
{
   DiadTraceVarVal val = var.getValueAt(for_trace,start_time);
   
   long qtime = start_time;
   
   IvyLog.logD("DIGEN","Build simple value: " + val);
   
   if (val.isNull(qtime)) return new DigenCodeFragment("null");
   
   DigenCodeFragment rslt = null;
   rslt = cur_context.getComputedValue(var); 
   if (rslt != null) return rslt;
   
   String typ = var.getDataType(start_time);
   JcompType jtyp = jcomp_typer.findType(typ);
   if (jtyp == null) return null;
   if (jtyp.isPrimitiveType()) {
      rslt = buildPrimitiveValue(jtyp,val);
    }
   else if (jtyp.isStringType()) {
      rslt = buildStringValue(val);
    }
   else if (jtyp.isEnumType()) {
      String efld = val.getStringValue(start_time);
      if (efld != null) {
         rslt = new DigenCodeFragment(jtyp.getName() + "." + efld);
       }
    }
   else if (jtyp.isArrayType()) {
      rslt = buildSimpleArrayValue(val,jtyp);
    }
   else if (jtyp.isBinaryType()) {
      rslt = buildSimpleSystemObjectValue(val,jtyp);
    }
   else if (jtyp.getName().equals("java.lang.Class")) {
      rslt = buildClassValue(val);
    }
   else if (jtyp.getName().equals("java.io.File")) {
      rslt = buildFileValue(val);
    }
   
   cur_context.noteComputed(val,rslt);
   
   return rslt;
}



/********************************************************************************/
/*                                                                              */
/*      Primitive types                                                         */
/*                                                                              */
/********************************************************************************/

DigenCodeFragment buildPrimitiveValue(JcompType typ,DiadTraceVarVal var)
{
   String val = var.getStringValue(start_time);
   String rslt = null;
   
   switch (typ.getName()) {
      case "int" :
         rslt = val;
         break;
      case "short" :
         rslt = "((short) " + val + ")";
         break;
      case "byte" :
         rslt = "((byte) " + val + ")";
         break;
      case "long" :
         if (val.endsWith("L") || val.endsWith("l")) rslt = val;
         else rslt = val + "L";
         break;
      case "char" :
         char cv = (char) Integer.parseInt(val);
         String sv = String.valueOf(cv);
         rslt = "'" + IvyFormat.formatChar(sv) + "'";
         break;
      case "float" :
         if (val.endsWith("F") || val.endsWith("F")) rslt = val;
         else rslt = val + "F";
         break;
      case "double" :
         if (val.contains(".") || val.contains("E") || val.contains("e")) {
            rslt = val;
          }
         else {
            rslt = val + ".0";
          }
         break;
      case "boolean" :
         if (val.equals("0") || val.equalsIgnoreCase("false")) rslt = "false";
         else rslt = "true";
         break;
    }
   
   if (rslt == null) return null;
   
   return new DigenCodeFragment(rslt);
}


private DigenCodeFragment buildStringValue(DiadTraceVarVal var)
{
   String val = var.getStringValue(start_time);
   String rslt = "\"" + IvyFormat.formatString(val) + "\"";
   
   return new DigenCodeFragment(rslt);
}


private DigenCodeFragment buildClassValue(DiadTraceVarVal var)
{
   String val = var.getStringValue(start_time);
   String rslt = val + ".class";
   
   return new DigenCodeFragment(rslt);
}


private DigenCodeFragment buildFileValue(DiadTraceVarVal var)
{
   String val = var.getStringValue(start_time);  
   String rslt = "new java.io.File(" + val + ")";
   
   return new DigenCodeFragment(rslt);
}



/********************************************************************************/
/*                                                                              */
/*      Handle arrays                                                           */
/*                                                                              */
/********************************************************************************/

private DigenCodeFragment buildSimpleArrayValue(DiadTraceVarVal rtv,JcompType typ)
{
   int sz = rtv.getArrayLength(for_trace,start_time);
   StringBuffer buf = new StringBuffer();
   buf.append("new " + typ.getBaseType() + "[" + sz + "]");
   if (sz > 0) {
      buf.append(" { ");
      for (int i = 0; i < sz; ++i) {
         DiadTraceVarVal etv = getIndexValue(rtv,i);
         DigenCodeFragment efg = buildSimpleValue(etv);
         if (efg == null) return null;
         if (i > 0) buf.append(" , ");
         buf.append(efg.getCode());
       }
      buf.append(" } ");
    }
   
   return new DigenCodeFragment(buf.toString());
}


/********************************************************************************/
/*                                                                              */
/*      Handle simple objects                                                   */
/*                                                                              */
/********************************************************************************/

private DigenCodeFragment buildSimpleSystemObjectValue(DiadTraceVarVal rtv,
      JcompType typ)
{
   switch (typ.getName()) {
      case "java.lang.Integer" :
      case "java.lang.Long" :
      case "java.lang.Short" :
      case "java.lang.Byte" :
      case "java.lang.Float" :
      case "java.lang.Double" :
      case "java.lang.Character" :
         String fnm = typ.getName() + ".value";
         DiadTraceVarVal rtv1 = getFieldValue(rtv,fnm);
         String val = "0";
         if (rtv1 != null) val = rtv1.getStringValue(start_time);
         String vcode = typ.getName() + ".valueOf(" + val + ")";
         return new DigenCodeFragment(vcode);
      default :
         break;
    }
   
   return null;
}



/********************************************************************************/
/*                                                                              */
/*      Ask the LLM to build an object                                          */
/*                                                                              */
/********************************************************************************/

private DigenCodeFragment askForCode(String typ,Map<String,DigenCodeFragment> vals)
{
   String var = cur_context.getNextVariable();
   
   String prompt = "In this case you should create an object of type " + typ;
   prompt += " with field values: \n";
   for (Map.Entry<String,DigenCodeFragment> ent : vals.entrySet()) {
      prompt += "  * " + ent.getKey() + " = " + ent.getValue().getCode() + ";\n";
    }
   prompt += "You should store the result in the variable " + var;
   
   DiadCandidate dc = test_creator.getCandidate();
   Element rslt = dc.askLimba(DiadAskType.BUILDER,prompt,true); 
   
   IvyLog.logD("DIGEN","ASK FOR CODE RETURNED " + " " +
         IvyXml.convertXmlToString(rslt));
   
   if (rslt != null) {
      String code = null;
      // use final result in the case where the LLM returns it multiple times
      for (Element jelt : IvyXml.children(rslt,"JAVA")) {
         code = IvyXml.getText(jelt);
       }
      if (code != null) {
         cur_context.addInitialization(code);
       }

      return new DigenCodeFragment(var);
    }
   
   return null;
}


}       // end of class DigenValueBuilder




/* end of DigenValueBuilder.java */

