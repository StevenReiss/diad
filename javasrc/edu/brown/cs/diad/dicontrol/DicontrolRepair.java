/********************************************************************************/
/*                                                                              */
/*              DicontrolEdits.java                                             */
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



package edu.brown.cs.diad.dicontrol;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadRepair;
import edu.brown.cs.diad.dicore.DiadTextEdit;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

 class DicontrolRepair implements DiadRepair 
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private List<DicontrolEdit> base_edits;
private Map<String,List<DicontrolEdit>> file_edits;
private Map<File,LineMap> line_maps;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DicontrolRepair(Element edits)
{
   base_edits = new ArrayList<>();
   
   file_edits = new HashMap<>();
   
   for (Element exml : IvyXml.children(edits,"EDIT")) {
      DicontrolEdit edit = new DicontrolEdit(exml);
      base_edits.add(edit);
      String fnm = edit.getFileName();
      List<DicontrolEdit> fed = file_edits.get(fnm);
      if (fed == null) {
         fed = new ArrayList<>();
         file_edits.put(fnm,fed);
       }
      fed.add(edit);
    }
   
   line_maps = new HashMap<>();
   for (Map.Entry<String,List<DicontrolEdit>> ent : file_edits.entrySet()) {
      File f = new File(ent.getKey());
      f = IvyFile.getCanonical(f);
      line_maps.put(f,new LineMap(ent.getValue()));
    }
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public int getMappedLine(File f0,int line)
{
   File f = IvyFile.getCanonical(f0);
   
   LineMap lm = line_maps.get(f);
   if (lm == null) return line;
   
   return lm.getMappedLine(line);
}



/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String outputEditXml() 
{ 
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.begin("EDITS");
   for (DicontrolEdit ed : base_edits) {
      ed.outputEditXml(xw);
    }
   xw.end("EDITS");
   String cnts = xw.toString();
   xw.close();
   return cnts;
}



/********************************************************************************/
/*                                                                              */
/*      Text edit implementation                                                */
/*                                                                              */
/********************************************************************************/

private class DicontrolEdit implements DiadTextEdit {

   private Element edit_xml;
   
   DicontrolEdit(Element xml) {
      edit_xml = xml;
    }
   
   @Override public String getFileName() {
      return IvyXml.getAttrString(edit_xml,"FILE");
    }
   
   @Override public int getStartOffset() {
      return IvyXml.getAttrInt(edit_xml,"OFFSET");
    }
   
   @Override public int getEditNumber() {
      return IvyXml.getAttrInt(edit_xml,"NUMBER");
    }
   
   @Override public int getStartLine() {
      return IvyXml.getAttrInt(edit_xml,"STARTLINE");
    }
   
   @Override public int getAddCount() {
      return IvyXml.getAttrInt(edit_xml,"ADDLINES");
    }
   
   @Override public int getDeleteCount() {
      return IvyXml.getAttrInt(edit_xml,"DELLINES");
    }
   
   @Override public int getLength() { 
      return IvyXml.getAttrInt(edit_xml,"LENGTH");
    }
   
   @Override public String getReplace() { 
      String t = IvyXml.getTextElement(edit_xml,"REPLACE");
      if (t != null & t.isEmpty()) t = null;
      return t;
    }
   
   void outputEditXml(IvyXmlWriter xw) {
      xw.begin("EDIT");
      xw.field("FILE",getFileName());
      xw.field("OFFSET",getStartOffset());
      int len = getLength();
      xw.field("LENGTH",len);
      String r = getReplace();
      if (r == null) xw.field("TYPE","DELETE");
      else if (len == 0) xw.field("TYPE","INSERT");
      else xw.field("TYPE","REPLACE");
      if (r != null) xw.cdata(r);
      xw.end("EDIT");
    }
   
}       // end of inner class DicontrolEdit



/********************************************************************************/
/*                                                                              */
/*      Line map implementation for a file                                      */
/*                                                                              */
/********************************************************************************/

private class LineMap {
   
   private Map<Integer,Integer> changed_lines;
   private int max_line;
   private int max_delta;
   
   LineMap(List<DicontrolEdit> edits0) {
      changed_lines = new HashMap<>();
      max_line = 0;
      max_delta = 0;
      List<DiadTextEdit> edits = new ArrayList<>(edits0);
      // look at the edits first to last
      edits.sort(Collections.reverseOrder());
      for (DiadTextEdit ed : edits) {
         if (max_delta != 0) {
            for (int i = max_line; i < ed.getStartLine(); ++i) {
               changed_lines.put(i,i+max_delta);
             }
          }
         int st = ed.getStartLine();
         int add = ed.getAddCount();
         int del = ed.getDeleteCount();
         for (int i = 0; i < Math.max(add,del); ++i) {
            changed_lines.put(st+i,-st);
          }
         max_delta += add-del;
         max_line = st+del+1;
       }
      
    }
   
   int getMappedLine(int line) {
      Integer ln0 = changed_lines.get(line);
      if (ln0 != null) return ln0;
      
      if (line < max_line) return line;
      return line + max_delta;
    }
}



}       // end of class DicontrolEdits




/* end of DicontrolEdits.java */

