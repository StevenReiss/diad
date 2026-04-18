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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadSymptom;
import edu.brown.cs.diad.dicore.DiadConstants.DiadSymptomType;
import edu.brown.cs.diad.dicore.DiadConstants.DiadValueOperator;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSource;
import edu.brown.cs.ivy.xml.IvyXml;
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
private String aux_expr;
private String original_value;
private String target_value;
private DiadValueOperator value_operator;
private double target_precision;
private String in_file;
private int in_line;
private int in_offset;



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
   aux_expr = null;
   original_value = null;
   target_value = null;
   value_operator = DiadValueOperator.NONE; 
   target_precision = 0;
   in_file = null;
   in_line = -1;
   in_offset = -1;
}

DicontrolSymptom(Element xml)
{
   symptom_type = IvyXml.getAttrEnum(xml,"TYPE",DiadSymptomType.NONE);
   symptom_item = IvyXml.getTextElement(xml,"ITEM");
   original_value = IvyXml.getTextElement(xml,"VALUE");
   original_expr = IvyXml.getTextElement(xml,"ORIGINAL");
   aux_expr = IvyXml.getTextElement(xml,"AUX");
   target_value = IvyXml.getTextElement(xml,"TARGET");
   value_operator = IvyXml.getAttrEnum(xml,"OPERATOR",DiadValueOperator.NONE);
   target_precision = IvyXml.getAttrDouble(xml,"PRECISION",0);
   in_file = IvyXml.getAttrString(xml,"INFILE");
   in_line = IvyXml.getAttrInt(xml,"INLINE",-1);
   in_offset = IvyXml.getAttrInt(xml,"INOFFSET",-1);
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

@Override public String getAuxExpression()              { return aux_expr; }  

@Override public DiadValueOperator getSymptomOperator() { return value_operator; }

@Override public double getTargetPrecision()            { return target_precision; } 

@Override public String getInFile()                      { return in_file; }
@Override public int getInLine()                         { return in_line; }
@Override public int getInOffset()                       { return in_offset; }


@Override public void setSymptomType(DiadSymptomType typ)
{
   symptom_type = typ; 
}

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
 
@Override public void setAuxExpression(String v) 
{
   if (v != null) v = v.trim();
   aux_expr = v;
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

void setLocation(ASTNode node)
{
   if (node == null) return;
   
   CompilationUnit cu = (CompilationUnit) node.getRoot();
   JcompSource js = JcompAst.getSource(node);
   in_file = js.getFileName();
   in_line = cu.getLineNumber(node.getStartPosition());
   in_offset = node.getStartPosition();
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
         return getAssertionText();
      case EXCEPTION :
         return getExceptionText();
      case LIBRARY_EXCEPTION :
         return getLibraryExceptionText();
      case VARIABLE :
         return "variable '" + symptom_item + "' has the wrong value";
      case EXPRESSION :
        return "expression '" + symptom_item + "' has the wrong value";
      case LOCATION :
         return getLocationText();
      case NO_EXCEPTION :
         return "the program should have thrown the exception " + getSymptomItem();
    }
   return "symptom"; 
}



private String getAssertionText() 
{
   if (original_value != null && target_value != null) {
      String typ = null;
      String v = original_value;
      if (original_value.startsWith("(")) {
         int idx = original_value.indexOf(") ");
         if (idx > 0) {
            typ = original_value.substring(1,idx).trim();
            v = original_value.substring(idx+2);
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
      else {
         buf.append("the value ");
       }
      if (original_expr != null && 
            !original_value.contains(original_expr)) {
         buf.append("computed from ```" + original_expr + "```, ");
         buf.append(v);
         buf.append(", ");
       }
      else {
         buf.append(v);
         buf.append(" ");
       }
      addOperatorInfo(buf);
      buf.append(target_value);
      return buf.toString();
    }
   IvyLog.logE("DICONTROL","Bad assertion check " + original_value + " " + 
         target_value);
   return "assertion failed";
}



private String getExceptionText()
{
   StringBuffer buf = new StringBuffer();
   
   if (getSymptomItem() != null) {
      buf.append("the programs throws the exception ");
      buf.append(getSymptomItem());
      buf.append(" ");
    }
   else {
      buf.append("the program throws an exception ");
    }
   if (original_expr != null) {
      buf.append("in ```");
      buf.append(original_expr);
      buf.append("``` ");
    }
   if (original_value != null) {
      buf.append("because the value ");
    }
   if (aux_expr != null && original_value != null &&
         !original_value.contains(original_expr)) {
      buf.append("computed from ```" + aux_expr + "```, ");
      buf.append(original_value);
      buf.append(", ");
    }
   else if (original_value != null) {
      buf.append(original_value);
      buf.append(" ");
    }
   addOperatorInfo(buf);
   if (target_value != null) {
      buf.append(target_value);
    }
   return buf.toString();
}


private String getLibraryExceptionText()
{
   StringBuffer buf = new StringBuffer();
   
   buf.append("the library call ");
   buf.append(original_expr);
   buf.append(" throws the exception");
   buf.append(symptom_item);
   
   return buf.toString();
}



private void addOperatorInfo(StringBuffer buf)
{
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
}



private String getLocationText()
{
   StringBuffer buf = new StringBuffer();
   
   buf.append("execution should not reach ");
   if (symptom_item == null) {
      buf.append("this breakpoint");
    }
   else {
      buf.append(symptom_item);
    }
   
   return buf.toString();
}


/********************************************************************************/
/*                                                                              */
/*      XML output methods                                                      */
/*                                                                              */
/********************************************************************************/

@Override public void outputXml(IvyXmlWriter xw)
{
   xw.begin("SYMPTOM");
   xw.field("TYPE",symptom_type);
   xw.field("OPERATOR",value_operator);
   if (value_operator != null && value_operator != DiadValueOperator.NONE) {
      if (target_precision != 0) xw.field("PRECISION",target_precision);
    }
   
   if (in_file != null) {
      xw.field("INFILE",in_file);
      if (in_line > 0) xw.field("INLINE",in_line);
      if (in_offset >= 0) xw.field("INOFFSET",in_offset);
    }
   
   if (symptom_item != null) xw.textElement("ITEM",symptom_item);
   if (original_value != null) xw.cdataElement("VALUE",original_value);
   if (original_expr != null) xw.cdataElement("ORIGINAL",original_expr);
   if (aux_expr != null) xw.cdataElement("AUX",aux_expr);
   if (target_value != null) xw.cdataElement("TARGET",target_value);
   
   xw.end("SYMPTOM");
}


}       // end of class DicontrolSymptom




/* end of DicontrolSymptom.java */

