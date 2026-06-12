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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import edu.brown.cs.diad.dicore.DiadTrace;
import edu.brown.cs.diad.dicore.DiadTrace.DiadTraceVarVal;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompSymbol;
import edu.brown.cs.ivy.jcomp.JcompType;
import edu.brown.cs.ivy.jcomp.JcompTyper;

class DigenValueBuilder implements DigenConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadTrace       for_trace;
private long            start_time;
private JcompTyper       jcomp_typer;
private JcompType       collection_type;
private JcompType       map_type;
private DigenValueContext cur_context;


private List<DiadTraceVarVal>   work_queue;
private Set<DiadTraceVarVal>    done_values;

private static final double SCORE_PUBLIC = 4;
private static final double SCORE_PACKAGE = 2;
private static final double SCORE_CONSTRUCTOR = 6;
private static final double SCORE_FACTORY = 8;
private static final double SCORE_FIELD = 10;
private static final double SCORE_ANY_FIELD = 5;
private static final double SCORE_DEFAULT = 0;
private static final double SCORE_VALUE = 1;
private static final double SCORE_ANY_VALUE = 2;


private static AtomicInteger variable_counter = new AtomicInteger(0);
private static AtomicInteger string_counter = new AtomicInteger(0);



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DigenValueBuilder(DigenManager mgr,DiadTrace trace,long start,JcompTyper typer)
{
   for_trace = trace;
   start_time = start;
   jcomp_typer = typer;
   collection_type = typer.findSystemType("java.util.Collection");
   map_type = typer.findSystemType("java.util.Map");
   work_queue = new ArrayList<>();
   done_values = new HashSet<>();
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
// if (work_queue.isEmpty()) {
//    boolean fg = setupInitializations();
//    if (!fg) return null;
//    work_queue.clear();
//  }
   
   return cur_context;
}


/********************************************************************************/
/*                                                                              */
/*      Add item to evaluate                                                    */
/*                                                                              */
/********************************************************************************/

void computeVarValue(DiadTraceVarVal var)
{
   if (var == null) return;
   IvyLog.logD("DIGEN","Work on variable " + var.getName());
   DiadTraceVarVal val = var.getValueAt(for_trace,start_time);
   computeValue(val);
}


void computeValue(DiadTraceVarVal val)
{
   if (val == null || done_values.contains(val)) return;
   done_values.add(val);
   IvyLog.logD("DIGEN","Compute value " + val);
   
   DigenCodeFragment pcf = buildSimpleValue(val);
   if (pcf == null) {
      queueValues(val);
    }
}


/********************************************************************************/
/*                                                                              */
/*      Queue value to work on later                                            */
/*                                                                              */
/********************************************************************************/

private void queueValues(DiadTraceVarVal val)
{
   long qtime = start_time;
   String typ = val.getDataType(qtime);
   JcompType jtyp = jcomp_typer.findType(typ);
   
   DiadTraceVarVal ftv =getFieldValue(val,"@toArray");
   if (ftv != null) {
      int ct = ftv.getArrayLength(start_time);
      for (int i = 0; i < ct; ++i) {
         DiadTraceVarVal etv = getIndexValue(ftv,i);
         computeValue(etv);
       } 
    }
   else if (jtyp.isCompatibleWith(map_type)) {
      DiadTraceVarVal ftv1 = getFieldValue(val,"@toArray");
      if (ftv1 != null) {
         int ct = ftv1.getArrayLength(qtime);
         for (int i = 0; i < ct; ++i) {
            DiadTraceVarVal etv = getIndexValue(ftv1,i);
            computeValue(etv);
          } 
       }
    }
   else if (jtyp.isArrayType()) {
      int ct = val.getArrayLength(qtime);
      for (int i = 0; i < ct; ++i) {
         DiadTraceVarVal ftv1 = getIndexValue(val,i);
         computeValue(ftv1);
       }
    }
   else {
      Map<String,JcompType> flds = jtyp.getFields(jcomp_typer);
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
         computeValue(ftv1);
       }
    }
   
   IvyLog.logD("DIGEN","Queue value " + val);
   work_queue.add(val);
}



/********************************************************************************/
/*                                                                              */
/*      Variable access methods                                                 */
/*                                                                              */
/********************************************************************************/

DiadTraceVarVal getIndexValue(DiadTraceVarVal val,int idx)
{
   DiadTraceVarVal val1 = val.getChild(Integer.toString(idx),start_time);
   return val1.getValueAt(for_trace,start_time);
}


DiadTraceVarVal getFieldValue(DiadTraceVarVal val,String fld)
{
   DiadTraceVarVal val1 = val.getChild(fld,start_time);
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
   
   IvyLog.logD("DIGEN","Build simple value: " + var);
   
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
         if (val.contains(".") || val.contains("E") || val.contains("e")) 
            rslt = val;
         else rslt = val + ".0";
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
   int sz = rtv.getArrayLength(start_time);
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
/*      Handle objects                                                          */
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



}       // end of class DigenValueBuilder




/* end of DigenValueBuilder.java */

