/********************************************************************************/
/*                                                                              */
/*              DiruntimeValue.java                                             */
/*                                                                              */
/*      Representation of a run time value                                      */
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

import edu.brown.cs.diad.dicore.DiadDataType;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

abstract class DiruntimeValue implements DiadValue, DiruntimeConstants, 
      DiruntimeConstants.DiruntimeGenericValue
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiruntimeType         value_type; 



/********************************************************************************/
/*                                                                              */
/*      Static factory methods                                                  */
/*                                                                              */
/********************************************************************************/

static DiruntimeValue nullValue(DiruntimeType typ)
{
   return new NullValue(typ);
}


static DiruntimeValue booleanValue(DiruntimeType typ,String val)
{
   boolean v = Boolean.valueOf(val);
   return new BooleanValue(typ,v);
}

static DiruntimeValue numericValue(DiruntimeType typ,String val)
{ 
   try {
      Long lval = Long.parseLong(val);
      return new NumericValue(typ,lval);
    }
   catch (NumberFormatException e) { }
   try {
      Double dval = Double.parseDouble(val);
      return new NumericValue(typ,dval);
    }
   catch (NumberFormatException e) { }
   
   return new NumericValue(typ,0);
}



static DiruntimeValue numericValue(DiruntimeType typ,long val)
{
   return new NumericValue(typ,val);
}


static DiruntimeValue stringValue(DiruntimeType typ,String val)
{
   return new StringValue(typ,val);
}


static DiruntimeValue objectValue(DiruntimeType typ,
      Map<String,DiruntimeGenericValue> inits) 
{
   return new ObjectValue(typ,inits);
}


static DiruntimeValue arrayValue(DiruntimeType typ,int len,Map<Integer,DiruntimeGenericValue> inits)
{
   return new ArrayValue(typ,len,inits);
}


static DiruntimeValue classValue(DiruntimeType ctyp,DiruntimeType typ)
{
   return new ClassValue(ctyp,typ);
}


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

protected DiruntimeValue(DiruntimeType typ) 
{
   value_type = typ;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public void setFieldValue(String name,DiadValue value) throws DiadException
{
   throw new DiadException("Value is not an object");
}

@Override public DiruntimeValue getFieldValue(String name) throws DiadException
{
   throw new DiadException("Value is not an object");
}

@Override public void setArrayElement(int idx,DiadValue value) throws DiadException
{
   throw new DiadException("Value is not an array");
} 


@Override public DiruntimeValue getArrayElement(int idx) throws DiadException
{
   throw new DiadException("Value is not an array");
}


@Override public boolean isNull()                        { return false; }


@Override public boolean getBoolean()   
{
   throw new IllegalArgumentException("Non-boolean value");
}

@Override public long getInt()
{
   throw new IllegalArgumentException("Non-numeric value");
}

@Override public String getString()
{
   throw new IllegalArgumentException("Non-string value");
}



@Override public DiadDataType getDataType()               { return value_type; }  


@Override public String getJavaValue()                    { return toString(); }



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

public void outputXml(IvyXmlWriter xw)
{
   xw.begin("VALUE");
   xw.field("TYPE",value_type.getName());
   localOutputXml(xw);
   xw.end("VALUE");
}



protected abstract void localOutputXml(IvyXmlWriter xw);




/********************************************************************************/
/*                                                                              */
/*      Null value                                                              */
/*                                                                              */
/********************************************************************************/

private static class NullValue extends DiruntimeValue {

   NullValue(DiruntimeType typ) {
      super(typ);
    }
   
   @Override public boolean isNull()           { return true; }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("NULL",true);
      xw.field("VALUE","null");
    }
   
   @Override public String toString()          { return "null"; }
   
}       // end of inner class NullValue



/********************************************************************************/
/*                                                                              */
/*      Boolean values                                                          */
/*                                                                              */
/********************************************************************************/

private static class BooleanValue extends DiruntimeValue {
   
   private boolean cur_value;
   
   BooleanValue(DiruntimeType typ,boolean v) {
      super(typ);
      cur_value = v;
    }
   
   @Override public boolean getBoolean()                 { return cur_value; }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("BOOLEAN",cur_value);
      xw.field("VALUE",cur_value);
    }
   
   @Override public String toString() {
      return Boolean.toString(cur_value);
    }

}       // end of inner class BooleanValue



/********************************************************************************/
/*                                                                              */
/*      Numeric values                                                          */
/*                                                                              */
/********************************************************************************/

private static class NumericValue extends DiruntimeValue {
   
   private Number cur_value;
   
   NumericValue(DiruntimeType typ,Number v) {
      super(typ);
      cur_value = v;
    }
   
   @Override public long getInt() {
      return cur_value.longValue();
    }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("NUMBER",cur_value);
      xw.field("VALUE",cur_value);
    }
   
   @Override public String getJavaValue() {
      switch (getDataType().getName()) {
         case "int" :
            return Integer.toString(cur_value.intValue());
         case "short" :
            return "((short) " + Short.toString(cur_value.shortValue()) + ")";
         case "byte" :
            return "((byte) " + Byte.toString(cur_value.byteValue()) + ")";
         case "long" :
            return Long.toString(cur_value.longValue()) + "L";
         case "float" :
            return Float.toString(cur_value.floatValue()) + "F";
         case "double" :
            return Double.toString(cur_value.doubleValue());
         case "char" :
            return "((char) " +  Short.toString(cur_value.shortValue()) + ")";
         default :
            break;
       }
      return toString();
    }
   
   @Override public String toString()           { return cur_value.toString(); }
   
}       // end of inner class IntegerValue



/********************************************************************************/
/*                                                                              */
/*      String values                                                           */
/*                                                                              */
/********************************************************************************/

private static class StringValue extends DiruntimeValue {
   
   private String cur_value;
   
   StringValue(DiruntimeType typ,String s) {
      super(typ);
      cur_value = s;
    }
   
   @Override public String getString() {
      return cur_value;
    }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("STRING",true);
      xw.cdataElement("CONTENTS",cur_value);
      xw.cdataElement("VALUE",cur_value);
    }
   
   @Override public String getJavaValue() {
      return "\"" + IvyFormat.formatString(cur_value) + "\"";
    }
   
   @Override public String toString()           { return "\"" + cur_value + "\""; }
   
}       // end of inner class StringValue



/********************************************************************************/
/*                                                                              */
/*      Class values                                                            */
/*                                                                              */
/********************************************************************************/

private static class ClassValue extends DiruntimeValue {
   
   private DiruntimeType base_type;
   
   ClassValue(DiruntimeType ctype,DiruntimeType base) {
      super(ctype);
      base_type = base;
    }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("CLASS",true);
      xw.cdataElement("BASE",base_type.toString());
    }
   
   @Override public String toString()           { return base_type.getName(); }
   
}       // end of inner class ClassValue

/********************************************************************************/
/*                                                                              */
/*      Object values                                                           */
/*                                                                              */
/********************************************************************************/

private static class ObjectValue extends DiruntimeValue {
   
   private Map<String,DiruntimeGenericValue> field_values;
   
   ObjectValue(DiruntimeType typ,Map<String,DiruntimeGenericValue> flds) {
      super(typ);
      if (flds == null) field_values = new HashMap<>();
      else field_values = new HashMap<>(flds);
    }
   
   @Override 
   public void setFieldValue(String nm,DiadValue val) {
      field_values.put(nm,(DiruntimeGenericValue) val);
    }
   
   @Override public DiruntimeValue getFieldValue(String nm) {
      DiruntimeGenericValue gv = field_values.get(nm);
      if (gv == null) {
         int idx = nm.lastIndexOf(".");
         if (idx < 0) return null;
         String nm1 = nm.substring(idx+1);
         gv = field_values.get(nm1);
       }
      if (gv == null) return null;
      if (gv instanceof DiruntimeDeferredValue) {
         DiruntimeDeferredValue dv = (DiruntimeDeferredValue) gv;
         gv = (DiruntimeGenericValue) dv.getValue();
         field_values.put(nm,gv);
       }
      return (DiruntimeValue) gv;
    }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("OBJECT",true);
      for (Map.Entry<String,DiruntimeGenericValue> ent : field_values.entrySet()) {
         xw.begin("FIELD");
         xw.field("NAME",ent.getKey());
         DiruntimeGenericValue gv = ent.getValue();
         if (gv instanceof DiruntimeDeferredValue) {
            xw.field("DEFERRED",true);
          }
         else if (gv instanceof DiruntimeValue) {
            DiruntimeValue fvl = (DiruntimeValue) gv;
            fvl.outputXml(xw);
          }
         xw.end("FIELD");
       }
    }
   
   @Override public String getJavaValue() {
      // this doesn't work -- is it needed?
      return toString();
    }
   
   @Override public String toString() {
      StringBuffer buf = new StringBuffer();
      buf.append("{ ");
      int ct = 0;
      for (Map.Entry<String,DiruntimeGenericValue> ent : field_values.entrySet()) {
         DiruntimeGenericValue gv = ent.getValue();
         if (gv instanceof DiruntimeValue) {
            DiruntimeValue bv = (DiruntimeValue) gv;
            if (bv.getDataType().isArrayType() || bv.getDataType().isObjectType()) continue;
            if (ct++ > 0) buf.append(",");
            buf.append(ent.getKey());
            buf.append(":");
            buf.append(bv.toString());
          }
       }
      buf.append("}");
      return buf.toString();
    }

}       // end of inner class ObjectValue



/********************************************************************************/
/*                                                                              */
/*      Array values                                                            */
/*                                                                              */
/********************************************************************************/

private static class ArrayValue extends DiruntimeValue {
   
   private Map<Integer,DiruntimeGenericValue> array_values;
   private int dim_size;
   
   ArrayValue(DiruntimeType typ,int dim,Map<Integer,DiruntimeGenericValue> elts) {
      super(typ);
      if (elts == null) array_values = new HashMap<>();
      else array_values = new HashMap<>(elts);
    }
   
   @Override public void setArrayElement(int idx,DiadValue val) throws DiadException {
      if (idx < 0 || idx >= dim_size) throw new DiadException("Index out of bounds");
      array_values.put(idx,(DiruntimeGenericValue) val);
    }
   
   @Override public DiruntimeValue getArrayElement(int idx) throws DiadException {
      if (idx < 0 || idx >= dim_size) throw new DiadException("Index out of bounds");
      DiruntimeGenericValue gv = array_values.get(idx);
      if (gv == null) return null;
      if (gv instanceof DiruntimeDeferredValue) {
         DiruntimeDeferredValue dv = (DiruntimeDeferredValue) gv; 
         gv = (DiruntimeGenericValue) dv.getValue();
         array_values.put(idx,gv);
       }
      return (DiruntimeValue) gv;
    }
   
   @Override protected void localOutputXml(IvyXmlWriter xw) {
      xw.field("ARRAY",true);
      for (Map.Entry<Integer,DiruntimeGenericValue> ent : array_values.entrySet()) {
         xw.begin("ELEMENT");
         xw.field("INDEX",ent.getKey());
         DiruntimeGenericValue gv = ent.getValue();
         if (gv instanceof DiruntimeDeferredValue) {
            xw.field("DEFERRED",true);
          }
         else if (gv instanceof DiruntimeValue) {
            DiruntimeValue fvl = (DiruntimeValue) gv;
            fvl.outputXml(xw);
          }
         xw.end("ELEMENT");
       }
    }
   
   @Override public String getJavaValue() {
      // this doesn't work -- is it needed?
      return toString();
    } 
   
   @Override public String toString() {
      StringBuffer buf = new StringBuffer();
      buf.append("[ ");
      int ct = 0;
      for (Map.Entry<Integer,DiruntimeGenericValue> ent : array_values.entrySet()) {
         DiruntimeGenericValue gv = ent.getValue();
         if (gv instanceof DiruntimeValue) {
            DiruntimeValue bv = (DiruntimeValue) gv;
            if (bv.getDataType().isArrayType() || bv.getDataType().isObjectType()) continue;
            if (ct++ > 0) buf.append(",");
            buf.append(ent.getKey());
            buf.append(":");
            buf.append(bv.toString());
          }
       }
      buf.append("]");
      return buf.toString();
    }
   
}       // end of iinner class ArrayValue


}       // end of class DiruntimeValue




/* end of DiruntimeValue.java */

