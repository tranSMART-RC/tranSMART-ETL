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
import fr.sanofi.fcl4transmart.model.classes.workUI.geneanalysis.MonitoringUI;
import fr.sanofi.fcl4transmart.model.interfaces.DataTypeItf;
import fr.sanofi.fcl4transmart.model.interfaces.StepItf;
import fr.sanofi.fcl4transmart.model.interfaces.WorkItf;
/**
 *This class represents the monitoring step for clinical data
 */	
public class Monitoring implements StepItf{
	private WorkItf workUI;
	private DataTypeItf dataType;
	public Monitoring(DataTypeItf dataType){
		this.workUI=new MonitoringUI(dataType);
		this.dataType=dataType;
	}
	@Override
	public WorkItf getWorkUI() {
		return this.workUI;
	}
	public String toString(){
		return "Monitoring";
	}
	public String getDescription(){
		return "This step allows accessing error logs for gene expression analysis results loading.\n"+
				"If an error has occurred while the kettle job was running, it is indicated, but details are given in an error file saved in the workspace.\n"+
				"If an error has occurred while the stored procedure was running, this error is detailed.\n"+
				"A database connection is needed for this step.";
	}
	public boolean isAvailable(){
		try{
			if(((GeneExpressionAnalysis)this.dataType).getAnalysisId()==null || ((GeneExpressionAnalysis)this.dataType).getAnalysisId().compareTo("")==0){
				return false;
			}
			if(((GeneExpressionAnalysis)this.dataType).getResultsFile()==null) return false;
			if(((GeneExpressionAnalysis)this.dataType).getKettleLog()==null) return false;
			return true;
		}
		catch(NullPointerException e){
			return false;
		}
	}
}
