/********************************************************************************/
/*                                                                              */
/*              DiexecuteChagnedItem.java                                       */
/*                                                                              */
/*      Data to handle finding changes.  Note this is immutable                 */
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

import edu.brown.cs.ivy.jcomp.JcompSymbol;

class DiexecuteChangedItem implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private JcompSymbol     ref_value;
private boolean         is_changed;
private boolean         is_relevant;




/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiexecuteChangedItem(JcompSymbol js)
{
   ref_value = js;
   is_changed = false;
   is_relevant = false;
}


DiexecuteChangedItem(JcompSymbol js,DiexecuteChangedItem base)
{
   ref_value = js;
   is_relevant = base.is_relevant;
   is_changed = true;
}



private DiexecuteChangedItem(DiexecuteChangedItem base,JcompSymbol js,boolean ch,boolean rl)
{
   ref_value = (js == null ? base.ref_value : js);
   is_changed = base.is_changed | ch;
   is_relevant = base.is_relevant | rl;
}



/********************************************************************************/
/*                                                                              */
/*      Access methods                                                          */
/*                                                                              */
/********************************************************************************/

JcompSymbol getReference()                      { return ref_value; }
boolean isChanged()                             { return is_changed; }
boolean isRelevant()                            { return is_relevant; }



/********************************************************************************/
/*                                                                              */
/*      Update methods                                                          */
/*                                                                              */
/********************************************************************************/

DiexecuteChangedItem changeReference(JcompSymbol js)
{
   if (js == null || js == ref_value) return this;
   return new DiexecuteChangedItem(this,js,false,false);
}



DiexecuteChangedItem setChanged()
{
   if (is_changed) return this;
   return new DiexecuteChangedItem(this,null,true,false);
}


DiexecuteChangedItem setRelevant()
{
   if (is_relevant) return this;
   return new DiexecuteChangedItem(this,null,false,true);
}


/********************************************************************************/
/*                                                                              */
/*      Output methods                                                          */
/*                                                                              */
/********************************************************************************/

@Override public String toString()
{
   String s = ref_value.toString();
   if (is_changed) s += "#";
   if (is_relevant) s += "@";
   return s;
}




/********************************************************************************/
/*                                                                              */
/*      Equality methods                                                        */
/*                                                                              */
/********************************************************************************/

@Override public int hashCode() 
{
   int hc = ref_value.hashCode();
   if (is_changed) hc += 100;
   if (is_relevant) hc += 200;
   return hc;
}




@Override public boolean equals(Object o) {
   if (o instanceof DiexecuteChangedItem) {
      DiexecuteChangedItem vd = (DiexecuteChangedItem) o;
      if (ref_value != vd.ref_value) return false; 
      if (is_changed != vd.is_changed) return false;
      if (is_relevant != vd.is_relevant) return false;
      return true;
    }
   return false;
}



}       // end of class DiexecuteChagnedItem




/* end of DiexecuteChagnedItem.java */

