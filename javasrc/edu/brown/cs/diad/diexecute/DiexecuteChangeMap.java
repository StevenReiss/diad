/********************************************************************************/
/*                                                                              */
/*              DiexecuteChangeMap.java                                         */
/*                                                                              */
/*      Map of changed items by symbol                                          */
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

import java.util.HashMap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompSymbol;

class DiexecuteChangeMap extends HashMap<JcompSymbol,DiexecuteChangedItem> implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/


private static final long serialVersionUID = 1;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteChangeMap()
{ }



/********************************************************************************/
/*                                                                              */
/*      Access Methods                                                          */
/*                                                                              */
/********************************************************************************/

public boolean shouldBeUsed(ASTNode n)
{
   RelevanceChecker rc = new RelevanceChecker();
   n.accept(rc);
   
   return rc.isChanged() || rc.isRelevant();
}



private class RelevanceChecker extends ASTVisitor {
   
   private boolean is_changed;
   private boolean is_relevant;
   
   RelevanceChecker() {
      is_changed = false;
      is_relevant = false;
    }
   
   boolean isChanged()                  { return is_changed; }
   boolean isRelevant()                 { return is_relevant; }
   
   @Override public void postVisit(ASTNode n) {
      JcompSymbol js = JcompAst.getReference(n);
      if (js == null) js = JcompAst.getDefinition(n);
      if (js == null) return;
      DiexecuteChangedItem tci = get(js);
      if (tci != null) {
         is_changed |= tci.isChanged();
         is_relevant |= tci.isRelevant();
       }
    }

}       // end of inner class RelevanceChecker

}       // end of class DiexecuteChangeMap




/* end of DiexecuteChangeMap.java */

