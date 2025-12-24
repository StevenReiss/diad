/********************************************************************************/
/*                                                                              */
/*              DiruntimeValueData.java                                         */
/*                                                                              */
/*      Representation of a partial value from the debugger                     */
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



package edu.brown.cs.diad.diruntime;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadDataType;
import edu.brown.cs.diad.dicore.DiadConstants.DiadValueKind;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;

class DiruntimeValueData implements DiruntimeConstants,
      DiruntimeConstants.DiruntimeGenericValue
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiruntimeThread for_thread;
private DiadValueKind   value_kind; 
private String val_name;
private String val_expr;
private String val_type;
private String val_value;
private boolean has_values;
private boolean is_local;
private boolean is_static;
private int array_length;
private Map<String,DiruntimeValueData> sub_values;
private DiruntimeValue result_value;
private int hash_code;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiruntimeValueData(DiruntimeThread sm,Element xml,String name)
{
   for_thread = sm;
   if (name == null) val_name = IvyXml.getAttrString(xml,"NAME");
   else val_name = name;
   val_expr = null;
   initialize(xml,null);
}

DiruntimeValueData(DiruntimeValueData par,Element xml)
{
   for_thread = par.for_thread;
   String vnm = IvyXml.getAttrString(xml,"NAME");
   if (par.val_expr != null) {
      val_expr = par.val_expr + "." + vnm;
    }
   String cnm = IvyXml.getAttrString(xml,"DECLTYPE");
   if (cnm != null) {
      vnm = getFieldKey(vnm,cnm);
    }
   val_name = par.val_name + "?" + vnm;
   
   initialize(xml,val_expr);
}


DiruntimeValueData(DiruntimeValue cv)
{
   for_thread = null;
   val_name = null;
   val_expr = null;
   initialize(null,null);
   result_value = cv;
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

DiadValueKind getKind()	{ return value_kind; }

String getType()		{ return val_type; }
String getValue()		{ return val_value; }

String getActualType()		{ return null; }
boolean hasContents()		{ return has_values; }
boolean isLocal()		{ return is_local; }
boolean isStatic()		{ return is_static; }

String getFrame() {
   return for_thread.getStack().getUserFrame().getFrameId(); 
}

String getThread()		{ return for_thread.getThreadId(); }

int getLength() 		{ return array_length; }


DiruntimeValue getDiadValue()
{
   if (result_value != null) return result_value;
   
   if (value_kind == DiadValueKind.UNKNOWN && val_type == null) {
      return null;
    }
   
   if (val_type != null && val_type.equals("null")) {
      return DiruntimeValue.nullValue(
            for_thread.findType("java.lang.Object"));
    }
   if (val_type != null && val_type.equals("void")) return null;
   
   DiruntimeType typ = for_thread.findType(val_type);
   
   switch (value_kind) {
      case PRIMITIVE :
	 if (typ.isBooleanType()) {
	    result_value = DiruntimeValue.booleanValue(
                  for_thread.findType("boolean"),val_value);
	  }
	 else if (typ.isNumericType()) {
	    result_value = DiruntimeValue.numericValue(typ,val_value);
	  }
	 break;
      case STRING :
	 result_value = DiruntimeValue.stringValue(for_thread.findType("java.lang.String"),
               val_value);
	 break;
      case OBJECT :
	 Map<String,DiruntimeGenericValue> inits = new HashMap<>();
	 Map<String,DiruntimeValueData> sets = new HashMap<>();
         if (typ.getFields() != null) {
            for (Map.Entry<String,DiadDataType> ent : typ.getFields().entrySet()) {
               String fnm = ent.getKey();
               String cnm = null;
               String key = fnm;
               int idx1 = fnm.lastIndexOf(".");
               if (idx1 >= 0) {
                  cnm = fnm.substring(0,idx1);
                  key = fnm.substring(idx1+1);
                }
               if (cnm == null) cnm = typ.getName();
               key = getKey(key,cnm);
               if (sub_values != null && sub_values.get(key) != null) {
                  DiruntimeValueData fsvd = sub_values.get(key);
                  fsvd = for_thread.getUniqueValue(fsvd); 
                  sets.put(fnm,fsvd);
                }
               else {
                  DeferredLookup def = new DeferredLookup(fnm);
                  inits.put(fnm,def);
                }
             }
          }
	 if (hash_code == 0) { 
	    inits.put(HASH_CODE_FIELD,new DeferredLookup(HASH_CODE_FIELD));
	  }
	 else {
	    DiruntimeValue hvl = DiruntimeValue.numericValue(
                  for_thread.intType(),hash_code); 
	    inits.put(HASH_CODE_FIELD,hvl);
	  }
	 result_value = DiruntimeValue.objectValue(typ,inits);
         
	 for (Map.Entry<String,DiruntimeValueData> ent : sets.entrySet()) {
	    DiruntimeValue cv = ent.getValue().getDiadValue();
	    try {
	       result_value.setFieldValue(ent.getKey(),cv);
	     }
	    catch (Exception e) {
	       IvyLog.logE("DIRUNTIME","Unexpected error setting field value",e);
	     }
	  }
	 break;
      case ARRAY :
	 if (array_length <= 1024) computeValues();
	 Map<Integer,DiruntimeGenericValue> ainits = new HashMap<>();
	 for (int i = 0; i < array_length; ++i) {
	    String key = "[" + i + "]";
	    String fullkey = getKey(key,null);
	    if (sub_values != null && sub_values.get(fullkey) != null) {
	       DiruntimeValueData fsvd = sub_values.get(fullkey);
	       fsvd = for_thread.getUniqueValue(fsvd);
	       ainits.put(i,fsvd.getDiadValue());
	     }
	    else {
	       DeferredLookup def = new DeferredLookup(key);
	       ainits.put(i,def);
	     }
	  }
	 result_value = DiruntimeValue.arrayValue(typ,array_length,ainits);
	 break;
      case CLASS :
	 int idx2 = val_value.lastIndexOf("(");
	 String tnm = val_value.substring(0,idx2).trim();
	 if (tnm.startsWith("(")) {
	    idx2 = tnm.lastIndexOf(")");
	    tnm = tnm.substring(1,idx2).trim();
	  }
         DiruntimeType ctyp = for_thread.findType(tnm);
	 result_value = DiruntimeValue.classValue(
               for_thread.findType("java.lang.Class"),ctyp);
	 break;
      case UNKNOWN :
	 break;
    }
   
   if (result_value == null) {
      IvyLog.logE("DIANALYSIS","Unknown conversion to cashew value from bubbles");
    }
   
   return result_value;
}


private String getKey(String fnm,String cnm)
{
   if (fnm.equals(HASH_CODE_FIELD)) return fnm;
   
   String knm = getFieldKey(fnm,cnm);
   
   return val_name + "?" + knm;
}



private String getFieldKey(String fnm,String cnm)
{
   if (fnm.equals(HASH_CODE_FIELD)) return fnm;
   
   if (fnm.startsWith("[")) return fnm;
   
   if (cnm != null) return cnm.replace("$",".") + "." + fnm;
   
   return fnm;
}



String findValue(DiruntimeValue cv,int lvl)
{
   if (result_value == null) return null;
   if (result_value == cv) return "";
   if (lvl == 0 || sub_values == null) return null;
   
   for (Map.Entry<String,DiruntimeValueData> ent : sub_values.entrySet()) {
      String r = ent.getValue().findValue(cv,lvl-1);
      if (r != null) {
	 if (array_length > 0) {
	    return "[" + ent.getKey() + "]";
	  }
	 else return "." + ent.getKey();
       }
    }
   
   return null;
}


/********************************************************************************/
/*										*/
/*	Setup methods								*/
/*										*/
/********************************************************************************/

private void initialize(Element xml,String expr)
{
   val_type = IvyXml.getAttrString(xml,"TYPE");
   value_kind = IvyXml.getAttrEnum(xml,"KIND",DiadValueKind.UNKNOWN);
   val_value = IvyXml.getTextElement(xml,"DESCRIPTION");
   if (val_value == null) val_value = "";
   if (value_kind == DiadValueKind.STRING && IvyXml.getAttrBool(xml,"CHARS")) {
      int len = IvyXml.getAttrInt(xml,"LENGTH");
      val_value = IvyXml.decodeCharacters(val_value,len);
      array_length = 0;
    }
   else {
      array_length = IvyXml.getAttrInt(xml,"LENGTH",0);
    }
   has_values = IvyXml.getAttrBool(xml,"HASVARS");
   is_local = IvyXml.getAttrBool(xml,"LOCAL");
   is_static = IvyXml.getAttrBool(xml,"STATIC");
   sub_values = null;
   hash_code = 0;
   val_expr = expr;
   addValues(xml);
}



private void addValues(Element xml)
{
   if (xml == null) return;
   for (Element e : IvyXml.children(xml,"VALUE")) {
      if (sub_values == null) sub_values = new HashMap<String,DiruntimeValueData>();
      DiruntimeValueData vd = new DiruntimeValueData(this,e);
      String nm = vd.val_name;
      vd = for_thread.getUniqueValue(vd);
      sub_values.put(nm,vd);
      // AcornLog.logD("ADD VALUE " + nm + " = " + vd);
    }
}



private synchronized void computeValues()
{
   if (!has_values || sub_values != null) return;
   if (val_expr == null) {
      Element root = for_thread.evaluateFields(val_name); 
      if (root != null) addValues(root);
    }
   else {
      DiruntimeValueData svd = for_thread.evaluateExpr(val_expr); 
      sub_values = svd.sub_values;
    }
}



void merge(DiruntimeValueData bvd)
{
   if (!has_values && bvd.has_values) {
      sub_values = bvd.sub_values;
      has_values = true;
      result_value = null;
    }
}

/********************************************************************************/
/*										*/
/*	Deferred value lookup							*/
/*										*/
/********************************************************************************/

private class DeferredLookup implements DiruntimeDeferredValue {
   
   private String field_name;
   
   DeferredLookup(String name) {
      field_name = name;
    }
   
   @Override public DiruntimeValue getValue() {
      computeValues();
      if (field_name.equals(HASH_CODE_FIELD)) {
         if (sub_values == null) sub_values = new HashMap<String,DiruntimeValueData>();
         if (sub_values.get(field_name) == null) {
            DiruntimeValueData svd = null;
            if (val_expr != null) {
               svd = for_thread.evaluateExpr("System.identityHashCode(" + val_expr + ")");
             }
            else {
               svd = for_thread.evaluateHashCode(val_name); 
             }
            if (svd != null) sub_values.put(field_name,svd);
          }
       }
      
      if (sub_values == null) return null;
      String fnm = field_name;
      String cnm = null;
      int idx = fnm.lastIndexOf(".");
      if (idx >= 0) {
         cnm = fnm.substring(0,idx);
         fnm = fnm.substring(idx+1);
       }
      if (cnm == null) {
         cnm = getType();
       }
      String lookup = getKey(fnm,cnm);
      DiruntimeValueData svd = sub_values.get(lookup);
      svd = for_thread.getUniqueValue(svd);
      if (svd == null) {
         IvyLog.logE("DIRUNTIME","Deferred Lookup of " + lookup + " not found");
         return null;
       }
      return svd.getDiadValue();
    }
   
}	// end of inner class DeferredLookup



/********************************************************************************/
/*										*/
/*	Debugging methods							*/
/*										*/
/********************************************************************************/

@Override public String toString()
{
   StringBuffer buf = new StringBuffer();
   buf.append("<<");
   buf.append(value_kind);
   buf.append(":");
   buf.append(val_type);
   buf.append("@");
   buf.append(val_value);
   if (array_length > 0) buf.append("#" + array_length);
   buf.append(" ");
   buf.append(val_name);
   buf.append(">>");
   return buf.toString();
}


}       // end of class DiruntimeValueData




/* end of DiruntimeValueData.java */

