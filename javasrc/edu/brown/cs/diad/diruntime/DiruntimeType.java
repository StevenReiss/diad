/********************************************************************************/
/*                                                                              */
/*              DiruntimeType.java                                              */
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



package edu.brown.cs.diad.diruntime;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadDataType;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

abstract class DiruntimeType implements DiruntimeConstants, DiadDataType 
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String          type_name;



/********************************************************************************/
/*                                                                              */
/*      Static creation methods                                                 */
/*                                                                              */
/********************************************************************************/

static DiruntimeType createNewType(DiruntimeProcess proc,String name)
{
   if (name.endsWith("[]")) return new ArrayType(proc,name);
   if (name.contains("<")) return new ParameterizedType(proc,name);
   
   switch (name) {
      case "boolean" :
         return new BooleanType();
      case "int" :
      case "short" :
      case "byte" :
      case "char" :
      case "long" :
         return new IntegerType(name);
      case "float" :
      case "double" :
         return new FloatingType(name);
      case "void" :
         return new VoidType();
      case "java.lang.String" :
         return new StringType();
    }
   
   return new ObjectType(proc,name);
}



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

private DiruntimeType(String name)
{
   type_name = name; 
   String vtype = type_name;
   if (vtype != null) {
      int idx = vtype.indexOf("<");
      int idx1 = vtype.lastIndexOf(">");
      if (idx >= 0) {
	 vtype = type_name.substring(0,idx);
	 if (idx1 > 0) vtype += type_name.substring(idx1+1);
       }
    }
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String getName() 
{
   return type_name;
}



@Override public boolean isBooleanType()
{
   return false;
}


@Override public boolean isNumericType()
{
   return false;
}

@Override public boolean isStringType()
{
   return false; 
}


@Override public boolean isArrayType()
{
   return false; 
}


@Override public boolean isObjectType()
{ 
   return false;
}

@Override public boolean isParameterizedType()
{
   return false;
}



@Override public DiruntimeType getBaseType()           
{
   return null;
}


@Override public Map<String,DiadDataType> getFields()
{
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Primitive types                                                         */
/*                                                                              */
/********************************************************************************/

private static class VoidType extends DiruntimeType {
   
   VoidType() {
      super("void");
    }
   
}       // end of inner class VoidType



private static class BooleanType extends DiruntimeType {
   
   BooleanType() {
      super("boolean");
    }
   
   @Override public boolean isBooleanType()                    { return true; }

}       // end of innerclass BooleanType



private static class IntegerType extends DiruntimeType {
   
   IntegerType(String name) {
      super(name);
    } 
   
   @Override public  boolean isNumericType()                    { return true; }
   
}       // end of inner class IntegerType



private static class FloatingType extends DiruntimeType {
   
   FloatingType(String name) {
      super(name);
    }
   
   @Override public boolean isNumericType()                    { return true; }
   
}       // end of inner class IntegerType



private static class StringType extends DiruntimeType {
   
   StringType() {
      super("java.lang.String");
    }
   
   @Override public boolean isStringType()                     { return true; }
   
}       // end of inner class StringType



private static class ArrayType extends DiruntimeType {
   
   private DiruntimeType base_type;
   
   ArrayType(DiruntimeProcess proc,String name) {
      super(name);
      int idx = name.lastIndexOf("[]");
      if (idx < 0) base_type = null;
      else {
         String basename = name.substring(0,idx).trim();
         base_type = proc.findType(basename);
       }
    }
   
   @Override public boolean isArrayType()               { return true; }
   @Override public DiruntimeType getBaseType()         { return base_type; }
   
}       // end of inner class ArrayType



private static class ParameterizedType extends DiruntimeType {
   
   private DiruntimeType base_type;
   
   ParameterizedType(DiruntimeProcess proc,String name) {
      super(name);
      int idx = name.indexOf("<");
      if (idx < 0) base_type = null;
      else {
         String basename = name.substring(0,idx).trim();
         base_type = proc.findType(basename);
       }
    }
   
   @Override public boolean isParameterizedType()       { return true; }
   @Override public DiruntimeType getBaseType()          { return base_type; }
   
}       // end of inner class ParameterizedType




private static class ObjectType extends DiruntimeType {
   
   private Map<String,DiadDataType>  field_map;
   private DiruntimeProcess           for_launch;
   
   ObjectType(DiruntimeProcess proc,String name) {
      super(name);
      for_launch = proc;
      field_map = null;
    }
   
   @Override public Map<String,DiadDataType> getFields() {
      if (field_map != null) return field_map;
      field_map = new HashMap<>();
      DiruntimeManager ctrl = for_launch.getManager();
      String pat = getName() + ".*";
      CommandArgs args = new CommandArgs("PATTERN",pat,"FOR","FIELD",
            "FIELDS",true,"DEFS",true,"REFS",false);
      Element xml = ctrl.sendBubblesMessage("PATTERNSEARCH",args,null);
      for (Element mat : IvyXml.children(xml,"MATCH")) {
         Element itm = IvyXml.getChild(mat,"ITEM");
         if (itm == null) continue;
         String typ = IvyXml.getAttrString(itm,"TYPE");
         if (typ == null || !typ.equals("Field")) continue;
         String key = IvyXml.getAttrString(itm,"KEY");
         int idx = key.indexOf(")");
         if (idx < 0) continue;
         String typ0 = key.substring(idx+1);
         String typ1 = IvyFormat.formatTypeName(typ0,true);
         String fnm = IvyXml.getAttrString(itm,"NAME");
         field_map.put(fnm,for_launch.findType(typ1));
       }
      if (field_map.isEmpty()) {
         String vtyp = null;
         switch (getName()) {
            case "java.lang.Integer" :
               vtyp = "int";
               break;
            case "java.lang.Short" :
               vtyp = "short";
               break;
            case "java.lang.Long" :
               vtyp = "long";
               break;
            case "java.lang.Byte" :
               vtyp = "byte";
               break;
            case "java.lang.Double" :
               vtyp = "double";
               break;
            case "java.lang.Float" :
               vtyp = "float";
               break;
            case "java.lang.Character" :
               vtyp = "char";
               break;
          }
         if (vtyp != null) {
            String fnm = getName() + ".value";
            field_map.put(fnm,for_launch.findType(vtyp));
          }
       }
      
      
      return field_map;
    }
   
   @Override public boolean isObjectType()              { return true; }
   
}       // end of inner class ObjectType




}       // end of class DiruntimeType




/* end of DiruntimeType.java */

