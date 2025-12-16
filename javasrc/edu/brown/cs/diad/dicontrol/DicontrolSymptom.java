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

class DicontrolSymptom implements DiadSymptom
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DiadSymptomType symptom_type;
private String symptom_item;
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
   original_value = null;
   target_value = null;
   value_operator = DiadValueOperator.NONE; 
   target_precision = 1e-5;
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

@Override public DiadValueOperator getSymptomOperator() { return value_operator; }

@Override public double getTargetPrecision()            { return target_precision; } 



}       // end of class DicontrolSymptom




/* end of DicontrolSymptom.java */

