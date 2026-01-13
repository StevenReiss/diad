/********************************************************************************/
/*                                                                              */
/*              DiruntimeVariable.java                                          */
/*                                                                              */
/*      Local variable on the stack                                             */
/*                                                                              */
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 *  Permission to use, copy, modify, and distribute this software and its	 *
 *  documentation for any purpose other than its incorporation into a		 *
 *  commercial product is hereby granted without fee, provided that the 	 *
 *  above copyright notice appear in all copies and that both that		 *
 *  copyright notice and this permission notice appear in supporting		 *
 *  documentation, and that the name of Brown University not be used in 	 *
 *  advertising or publicity pertaining to distribution of the software 	 *
 *  without specific, written prior permission. 				 *
 *										 *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS		 *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND		 *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY	 *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY 	 *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,		 *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS		 *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE 	 *
 *  OF THIS SOFTWARE.								 *
 *										 *
 ********************************************************************************/


package edu.brown.cs.diad.diruntime;

import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadLocalVariable;
import edu.brown.cs.ivy.xml.IvyXml;

class DiruntimeVariable implements DiruntimeConstants, DiadLocalVariable
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String var_kind;
private String var_type;
private String var_value;
private String var_name;
private int    var_length;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiruntimeVariable(Element xml)
{
   var_name = IvyXml.getAttrString(xml,"NAME");
   var_kind = IvyXml.getAttrString(xml,"KIND");
   var_type = IvyXml.getAttrString(xml,"TYPE");
   var_value = IvyXml.getTextElement(xml,"DESCRIPTION");
   var_length = IvyXml.getAttrInt(xml,"LENGTH");
   if (IvyXml.getAttrBool(xml,"CHARS")) {
      var_value = IvyXml.decodeCharacters(var_value,var_length);
    }
   else {
      var_value = IvyXml.decodeXmlString(var_value);
    }
   if (var_type != null && var_type.equals("java.lang.Class")) {
      var_kind = "CLASS";
    }
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String getName()               { return var_name; }

@Override public String getType()               { return var_type; }

@Override public String getKind()               { return var_kind; }

@Override public String getValue()              { return var_value; }



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public JSONObject toJson()
{
   JSONObject jo = new JSONObject();
   
   String kind = var_kind;
   if (kind == null) kind = "OBJECT";
   
   jo.put("NAME",var_name);
   jo.put("TYPE",var_type);
   jo.put("KIND",kind);
   
   switch (kind) {
      default :
      case "UNKNOWN" :
      case "OBJECT" :
      case "CLASS" :
      case "SCOPE" :
         if (var_type.equals("null")) jo.put("VALUE",(Object) null);
         else jo.put("VALUE",var_value);
         break;
      case "ARRAY" :
      case "CHARS" :
         jo.put("LENGTH",var_length);
         jo.put("VALUE",var_value);
         break;
      case "PRIMITIVE" :
         switch (var_type) {
            case "byte" :
            case "short" :
            case "int" :
               int vint = Integer.parseInt(var_value);
               jo.put("VALUE",vint);
               break;
            case "long" :
               long vlong = Long.parseLong(var_value);
               jo.put("VALUE",vlong);
               break;
            case "double" :
            case "float" :
               double vdbl = Double.parseDouble(var_value);
               jo.put("VALUE",vdbl);
               break;
            case "boolean" :
               boolean vbool = "tTyY1".indexOf(var_value.charAt(0)) >= 0;
               jo.put("VALUE",vbool);
               break;
            case "char" :
               if (var_value.startsWith("'")) {
                  jo.put("VALUE",var_value);
                }
               else {
                  int vchar = Integer.parseInt(var_value);
                  jo.put("VALUE",vchar); 
                }
            default :
               jo.put("VALUE",var_value);
               break;
          }
         break;
    }
   jo.put("VALUE",var_value);
   
   return jo;
}




}       // end of class DiruntimeVariable




/* end of DiruntimeVariable.java */

