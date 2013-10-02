/*******************************************************************************
 * Copyright (c) 2012 Sanofi-Aventis Recherche et Développement.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sanofi-Aventis Recherche et Développement - initial API and implementation
 ******************************************************************************/
package fr.sanofi.fcl4transmart.model.classes.steps.geneanalysis;

import fr.sanofi.fcl4transmart.model.classes.dataType.GeneExpressionAnalysis;
import fr.sanofi.fcl4transmart.model.classes.workUI.geneanalysis.CheckAnnotationUI;
import fr.sanofi.fcl4transmart.model.interfaces.DataTypeItf;
import fr.sanofi.fcl4transmart.model.interfaces.StepItf;
import fr.sanofi.fcl4transmart.model.interfaces.WorkItf;
/**
 *This class represents the step to change a study name
 */	
public class CheckAnnotation implements StepItf{
	private WorkItf workUI;
	private DataTypeItf dataType;
	public CheckAnnotation(DataTypeItf dataType){
		this.workUI=new CheckAnnotationUI(dataType);
		this.dataType=dataType;
	}
	@Override
	public WorkItf getWorkUI() {
		return this.workUI;
	}
	public String toString(){
		return "Select annotation file (optional)";
	}
	public String getDescription(){
		return "This step allows selecting a file corresponding to the annotation, so that eventual new probes can be loaded.\n"+
				"This step is optional, but if an annotation has never been loaded, the gene expression analysis results loading will fail.\n"+
				"This file has to be in Afymetrix NetAffy CSV format.";
	}
	public boolean isAvailable(){
		if(((GeneExpressionAnalysis)this.dataType).getAnalysisId()==null || ((GeneExpressionAnalysis)this.dataType).getAnalysisId().compareTo("")==0){
			return false;
		}
		if(((GeneExpressionAnalysis)this.dataType).getResultsFile()==null) return false;
		return true;
	}
}
