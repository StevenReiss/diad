/********************************************************************************/
/*                                                                              */
/*              DicontrolSymptom.java                                           */
/*                                                                              */
/*      Implementation of a symptom                                             */
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



package edu.brown.cs.diad.dicontrol;

import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadConstants.DiadSymptomType;
import edu.brown.cs.diad.dicore.DiadConstants.DiadValueOperator;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DicontrolSymptom implements DiadSymptom
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadSymptomType symptom_type;
private String symptom_item;
private String original_expr;
private String original_value;
private String target_value;
private DiadValueOperator value_operator;
private double target_precision;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolSymptom(DiadSymptomType type) 
{
   this(type,null);
}


DicontrolSymptom(DiadSymptomType type,String item)
{
   symptom_type = type;
   symptom_item = item;
   original_expr = null;
   original_value = null;
   target_value = null;
   value_operator = DiadValueOperator.NONE; 
   target_precision = 0;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public DiadSymptomType getSymptomType()       { return symptom_type; }

@Override public String getSymptomItem()                { return symptom_item; }

@Override public String getOriginalValue()              { return original_value; }

@Override public String getTargetValue()                { return target_value; }

@Override public String getOriginalExpression()         { return original_expr; } 

@Override public DiadValueOperator getSymptomOperator() { return value_operator; }

@Override public double getTargetPrecision()            { return target_precision; } 

@Override public void setSymptomItem(String v) 
{
   if (v != null) v = v.trim();
   symptom_item = v;
}

@Override public void setOriginalValue(String v)
{
   original_value = v;
   if (original_expr == null) original_expr = v;
}

@Override public void setOriginalExpression(String v) 
{
   if (v != null) v = v.trim();
   original_expr = v;
}

@Override public void setTargetValue(String v)
{
   target_value = v;
}

@Override public void setOperator(DiadValueOperator op)
{
   value_operator = op;
}

@Override public void setPrecision(double p) 
{
   target_precision = p; 
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String getText()
{
   switch (symptom_type) {
      case ASSERTION :
         if (original_value != null && target_value != null) {
            String typ = null;
            String v = original_value;
             if (original_value.startsWith("(")) {
                int idx = original_value.indexOf(") ");
                if (idx > 0) {
                   typ = original_value.substring(1,idx).trim();
                   v = original_value.substring(idx+3);
                 }
              }
             StringBuffer buf = new StringBuffer();
             if (symptom_item == null) {
                buf.append("an assertion failed because ");
              }
             else {
                buf.append("the assertion ```");
                buf.append(symptom_item);
                buf.append("``` failed because ");
              }
             if (typ != null) {
                if (typ.equals("java.lang.String")) typ = "string";
                buf.append("the " + typ + " value ");
              }
             if (original_expr != null && 
                   !original_value.contains(original_expr)) {
                buf.append(" computed from " + original_expr + ", ");
                buf.append(v);
                buf.append(", ");
              }
             else {
                buf.append(v);
              }
           
             switch (value_operator) {
                case EQL :
                default :
                   buf.append(" should be equal to ");
                   break;
                case NEQ :
                   buf.append(" should not be equal to ");
                   break;
                case GTR :
                   buf.append(" should be greater than ");
                   break;
                case GEQ :
                   buf.append(" should be greater than or equal to ");
                   break;
                case LSS :
                   buf.append(" should be less than ");
                   break;
                case LEQ :
                   buf.append(" should be less than or equal to ");
                   break;
              }
             buf.append(target_value);
             return buf.toString();
          }
         return "assertion failed";
      case EXCEPTION :
      case CAUGHT_EXCEPTION :
         return "the program throws the exception " + getSymptomItem();
      case EXPRESSION :
        return "expression has the wrong value";
      case LOCATION :
         return "execution should not have gotten here";
      case NO_EXCEPTION :
         return "the program should have thrown theexception " + getSymptomItem();
      case VARIABLE :
         return "variable has the wrong value";
    }
   return "symptom"; 
}


@Override public void outputXml(IvyXmlWriter xw)
{
   xw.begin("SYMPTOM");
   xw.field("TYPE",symptom_type);
   xw.field("OPERATOR",value_operator);
   if (value_operator != null && value_operator != DiadValueOperator.NONE) {
      if (target_precision != 0) xw.field("PRECISION",target_precision);
    }
   if (symptom_item != null) xw.textElement("ITEM",symptom_item);
   if (original_value != null) xw.cdataElement("ORIGINAL",original_value);
   if (target_value != null) xw.cdataElement("TARGET",target_value);
   xw.end("SYMPTOM");
}


}       // end of class DicontrolSymptom




/* end of DicontrolSymptom.java */

