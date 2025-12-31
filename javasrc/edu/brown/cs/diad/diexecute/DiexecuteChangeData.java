/********************************************************************************/
/*                                                                              */
/*              DiexecuteChangeData.java                                        */
/*                                                                              */
/*      Information about a possibly changed item                               */
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.ivy.jcomp.JcompSymbol;

class DiexecuteChangeData implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private Map<DiadStackFrame,DiexecuteChangeMap> frame_changes;
private DiadStackFrame                     top_frame;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteChangeData()
{
   frame_changes = new HashMap<>();
   top_frame = null;
}


/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

void setChanges(DiadStackFrame f,DiexecuteChangeMap tcm)
{
   frame_changes.put(f,tcm);
}



public DiexecuteChangeMap getChanges(DiadStackFrame f)
{
   return frame_changes.get(f);
}


void setTopFrame(DiadStackFrame f)
{
   top_frame = f;
}


DiadStackFrame getTopFrame()
{
   return top_frame;
}


/********************************************************************************/
/*                                                                              */
/*      Get parameters for a frame                                              */
/*                                                                              */
/********************************************************************************/

public List<DiexecuteChangeVariable> getTopParameters() 
{
   return getParameters(top_frame);
}



List<DiexecuteChangeVariable> getParameters(DiadStackFrame f)
{
   List<DiexecuteChangeVariable> rslt = new ArrayList<>();
   DiexecuteChangeMap changes = getChanges(f);
   String cls = f.getClassName();
   
   for (DiexecuteChangedItem tcd : changes.values()) {
      JcompSymbol js = tcd.getReference();
      VarData vd = null;
      if (js.isFieldSymbol()) {
         if (tcd.isChanged() && tcd.isRelevant()) {
            if (js.getClassType().getName().equals(cls)) {
               vd = new VarData(js.getName(),DiexecuteVariableType.THIS_FIELD,tcd);
             }
            else {
               vd = new VarData(js.getFullName(),DiexecuteVariableType.FIELD,tcd);
             }
          }
       }
      else {
         ASTNode n = js.getDefinitionNode();
         if (n instanceof SingleVariableDeclaration && 
               n.getParent() instanceof MethodDeclaration) {
            // handle parameters
            if (tcd.isChanged()) {
               vd = new VarData(js.getName(),DiexecuteVariableType.PARAMETER,tcd);
             }
          }
         else {
            // handle locals
          }
       }
      if (vd != null) rslt.add(vd);
    }
   
   return rslt;
}




/********************************************************************************/
/*                                                                              */
/*      Variable Result structure                                               */
/*                                                                              */
/********************************************************************************/

private static class VarData implements DiexecuteChangeVariable {
   
   private String var_name; 
   private DiexecuteVariableType var_type;
   
   VarData(String nm,DiexecuteVariableType typ,DiexecuteChangedItem tcd) {
      var_name = nm;
      var_type = typ;
    }
   
   @Override public String getName()                    { return var_name; }
   @Override public DiexecuteVariableType getVariableType() { return var_type; }
   
   @Override public String toString() {
      return var_type.toString() + ":" + var_name;
    }
   
}       // end of inner class VarData




}       // end of class DiexecuteChangeData




/* end of DiexecuteChangeData.java */

