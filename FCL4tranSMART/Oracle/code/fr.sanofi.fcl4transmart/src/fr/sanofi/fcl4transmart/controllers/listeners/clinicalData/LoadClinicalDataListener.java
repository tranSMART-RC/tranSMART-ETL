/*******************************************************************************
 * Copyright (c) 2012 Sanofi-Aventis Recherche et D�veloppement.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *    Sanofi-Aventis Recherche et D�veloppement - initial API and implementation
 ******************************************************************************/
package fr.sanofi.fcl4transmart.controllers.listeners.clinicalData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.logging.CentralLogStore;
import org.pentaho.di.core.logging.Log4jBufferAppender;
import org.pentaho.di.core.util.EnvUtil;
import org.pentaho.di.i18n.GlobalMessages;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.JobMeta;

import fr.sanofi.fcl4transmart.controllers.RetrieveFm;
import fr.sanofi.fcl4transmart.handlers.PreferencesHandler;
import fr.sanofi.fcl4transmart.handlers.etlPreferences;
import fr.sanofi.fcl4transmart.model.classes.dataType.ClinicalData;
import fr.sanofi.fcl4transmart.model.classes.workUI.clinicalData.LoadDataUI;
import fr.sanofi.fcl4transmart.model.interfaces.DataTypeItf;
import fr.sanofi.fcl4transmart.ui.parts.WorkPart;

import org.eclipse.e4.core.di.extensions.*;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
/**
 *This class controls the clinical data loading
 */	
public class LoadClinicalDataListener implements Listener{
	private DataTypeItf dataType;
	private LoadDataUI loadDataUI;
	private ChannelSftp c;
	@SuppressWarnings("restriction")
	@Inject @Preference(nodePath="fr.sanofi.fcl4transmart") IEclipsePreferences preferences;

	public LoadClinicalDataListener(LoadDataUI loadDataUI, DataTypeItf dataType){
		this.dataType=dataType;
		this.loadDataUI=loadDataUI;
	}
	/**
	 *Loads the study:
	 *-Create the top folder if not existing
	 *-initiate Kettle environment
	 *-Find KEttle files
	 *-Set Ksttle parameters
	 *-Calls the Kettle job
	 *-Save the log file
	 */	
	@Override
	public void handleEvent(Event event) {
		loadDataUI.openLoadingShell();
		new Thread(){
			public void run() {
				String jobPath;
				String[] splited=loadDataUI.getTopNode().split("\\\\", -1);
				if(splited[0].compareTo("")!=0){
					loadDataUI.setMessage("A study node has to begin by the character '\\'");
					loadDataUI.setIsLoading(false);
					return;
				}
				//check if top node exists, add it
				try{
					Class.forName("oracle.jdbc.driver.OracleDriver");
					String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
					
					Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getMetadataUser(), PreferencesHandler.getMetadataPwd());
					Statement stmt = con.createStatement();
					ResultSet rs=stmt.executeQuery("select * from table_access where c_name='"+splited[1]+"'");
					if(!rs.next()){//have to add a top node
						stmt.executeQuery("insert into table_access("+
								"c_table_cd,"+
								"c_table_name,"+
								"c_protected_access,"+
								"c_hlevel,"+
								"c_fullname,"+
								"c_name,"+
								"c_synonym_cd,"+
								"c_visualattributes,"+
								"c_totalnum,"+
								"c_facttablecolumn,"+
								"c_dimtablename,"+
								"c_columnname,"+
								"c_columndatatype,"+
								"c_operator,"+
								"c_dimcode,"+
								"c_tooltip,"+
								"c_status_cd) values("+
								"'"+splited[1]+"',"+
								"'i2b2',"+
								"'N',"+
								"0,"+
								"'\\"+splited[1]+"\\',"+
								"'"+splited[1]+"',"+
								"'N',"+	
								"'CAP',"+
								"0,"+
								"'concept_cd',"+
								"'concept_dimension',"+
								"'concept_path',"+
								"'T',"+
								"'LIKE',"+
								"'\\"+splited[1]+"\\',"+
								"'\\"+splited[1]+"\\',"+
								"'A')"
							);
						
						//get folder id for program
						int id=RetrieveFm.getProgramId();
						String uid="FOL:"+String.valueOf(id);
						
						stmt.executeQuery("insert into i2b2 values(0, '\\"+splited[1]+"\\', '"+splited[1]+"','N','CAP',0,null, null, 'CONCEPT_CD','CONCEPT_DIMENSION','CONCEPT_PATH', 'T', 'LIKE','\\"+splited[1]+"\\', null, '\\"+splited[1]+"\\', sysdate, null, null, '"+uid+"', null, null, '@', null, null, null)");
					}
					
					
					con.close();
				}catch(SQLException e){
					e.printStackTrace();
					loadDataUI.setMessage("SQL exception: "+e.getLocalizedMessage());
					loadDataUI.setIsLoading(false);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					loadDataUI.setMessage("Class Not Found exception");
					loadDataUI.setIsLoading(false);
					return;
				}
	
				if(!loadDataUI.getEtlServer()){//do not use ETL server
					try{ 
						//initiate kettle environment
						GlobalMessages.setLocale(EnvUtil.createLocale("en-US"));
						URL kettleUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/lib/pentaho");
						kettleUrl = FileLocator.toFileURL(kettleUrl);  
						System.setProperty("KETTLE_PLUGIN_BASE_FOLDERS", kettleUrl.getPath());
						KettleEnvironment.init(false);
	
						//find the kettle job to initiate the loading
						URL jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/create_clinical_data.kjb");
						jobUrl = FileLocator.toFileURL(jobUrl);  
						jobPath = jobUrl.getPath();
						//create a new job from the kettle file
			
						JobMeta jobMeta = new JobMeta(jobPath, null);
						Job job = new Job(null, jobMeta);
						
						//find the other files needed for job and put them in the cache
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/validate_clinical_data_params.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/get_data_filenames.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/load_lt_clinical_data.kjb");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/map_data_to_std_format.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/run_i2b2_load_clinical_data.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/set_data_filename.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl);
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/cz_end_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/cz_start_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/cz_write_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl);
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/write_study_id_to_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/write_clinical_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/set_ETL_ID_variables.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/ETL.DSE.check_enroll_date.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/ETL.util.show_properties.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/ETL.util.test_connections.kjb");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/ETL.util.test_connections.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
			
						job.getJobMeta().setParameterValue("DATA_LOCATION", dataType.getPath().getAbsolutePath());
						job.getJobMeta().setParameterValue("COLUMN_MAP_FILE", ((ClinicalData)dataType).getCMF().getName());
						
						File sort=new File(dataType.getStudy().getPath().getParentFile().getAbsolutePath()+File.separator+".sort");
						if(!sort.exists()){
							FileUtils.forceMkdir(sort);
						}
						job.getJobMeta().setParameterValue("SORT_DIR", sort.getAbsolutePath());
						job.getJobMeta().setParameterValue("STUDY_ID", dataType.getStudy().toString());
						job.getJobMeta().setParameterValue("TOP_NODE", loadDataUI.getTopNode());
						if(((ClinicalData)dataType).getWMF()!=null){
							job.getJobMeta().setParameterValue("WORD_MAP_FILE", ((ClinicalData)dataType).getWMF().getName());
						}
						job.getJobMeta().setParameterValue("LOAD_TYPE", "I");
						job.getJobMeta().setParameterValue("TM_CZ_DB_SERVER", PreferencesHandler.getDbServer());
						job.getJobMeta().setParameterValue("TM_CZ_DB_NAME", PreferencesHandler.getDbName());
						job.getJobMeta().setParameterValue("TM_CZ_DB_PORT", PreferencesHandler.getDbPort());
						job.getJobMeta().setParameterValue("TM_CZ_DB_USER", PreferencesHandler.getTm_czUser());
						job.getJobMeta().setParameterValue("TM_CZ_DB_PWD", PreferencesHandler.getTm_czPwd());
						job.getJobMeta().setParameterValue("TM_LZ_DB_SERVER", PreferencesHandler.getDbServer());
						job.getJobMeta().setParameterValue("TM_LZ_DB_NAME", PreferencesHandler.getDbName());
						job.getJobMeta().setParameterValue("TM_LZ_DB_PORT", PreferencesHandler.getDbPort());
						job.getJobMeta().setParameterValue("TM_LZ_DB_USER", PreferencesHandler.getTm_lzUser());
						job.getJobMeta().setParameterValue("TM_LZ_DB_PWD", PreferencesHandler.getTm_lzPwd());
						
						if(loadDataUI.getSecurity()){
							job.getJobMeta().setParameterValue("SECURITY_REQUIRED", "Y");
						}else{
							job.getJobMeta().setParameterValue("SECURITY_REQUIRED", "N");
						}
	
						job.start();
						
						//wait for kettle to finish
						boolean kettleFinished=false;
						
						while(!job.isFinished() && !kettleFinished){
							Log4jBufferAppender appender = CentralLogStore.getAppender();
							String logText = appender.getBuffer(job.getLogChannelId(), false).toString();
							Pattern pattern=Pattern.compile(".*Starting entry \\[run i2b2_load_clinical_data\\].*", Pattern.DOTALL);
							Matcher matcher=pattern.matcher(logText);
							if(matcher.matches()){
							 	job.waitUntilFinished(2*60*1000);
							 	kettleFinished=true;
							}
						}
						
						if(!job.isFinished()){
							String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
							Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
							Statement stmt = con.createStatement();
							
							//remove rows for this study before adding new ones
							ResultSet rs=stmt.executeQuery("select max(JOB_ID) from CZ_JOB_AUDIT where STEP_DESC='Start i2b2_load_clinical_data%'");
							int jobId;
							if(rs.next()){
								jobId=rs.getInt("max(JOB_ID)");
							}
							else{
								con.close();
								loadDataUI.setIsLoading(false);
								return;
							}
							boolean procedureFinished=false;
							while(!job.isFinished() && !procedureFinished){
								rs=stmt.executeQuery("select * from cz_job_audit where step_desc like '%End i2b2_load_clinical_data%' and job_id="+jobId);
								if(rs.next()){
									procedureFinished=true;
								}
								job.waitUntilFinished(60*1000);
							}
							con.close();
						}
						
						@SuppressWarnings("unused")
						Result result = job.getResult();
						Log4jBufferAppender appender = CentralLogStore.getAppender();
						String logText = appender.getBuffer(job.getLogChannelId(), false).toString();
						Pattern pattern=Pattern.compile(".*Finished job entry \\[run i2b2_load_clinical_data\\] \\(result=\\[true\\]\\).*", Pattern.DOTALL);
						Matcher matcher=pattern.matcher(logText);
						if(matcher.matches()){
							String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
							Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
							Statement stmt = con.createStatement();
							
							//remove rows for study before adding new ones
							ResultSet rs=stmt.executeQuery("select max(JOB_ID) from CZ_JOB_AUDIT where STEP_DESC like 'Start i2b2_load_clinical_data%'");
							int jobId;
							if(rs.next()){
								jobId=rs.getInt("max(JOB_ID)");
							}
							else{
								con.close();
								loadDataUI.setMessage("Job identifier does not exist");
								loadDataUI.setIsLoading(false);
								return;
							}
							
							logText+="\nOracle job id:\n"+String.valueOf(jobId);
							con.close();
						}
						writeLog(logText);
						CentralLogStore.discardLines(job.getLogChannelId(), false);
					} 
					catch (Exception e1) {
						e1.printStackTrace();
						loadDataUI.setMessage("Kettle exception: "+e1.getLocalizedMessage());
						loadDataUI.setIsLoading(false);
						return;
					}
					loadDataUI.setIsLoading(false);
				}else{//etl server is used
					//transfer files in filesDirectory in ETL server
					try{
						JSch jsch=new JSch();
						Session session=jsch.getSession(etlPreferences.getUser(), etlPreferences.getHost(), Integer.valueOf(etlPreferences.getPort()));
						session.setPassword(etlPreferences.getPass());
					 
						java.util.Properties config = new java.util.Properties(); 
						config.put("StrictHostKeyChecking", "no");
						session.setConfig(config);
						
						session.connect();
					 
						Channel channel=session.openChannel("sftp");
						channel.connect();
						c=(ChannelSftp)channel;
										
						//try to go to the right directory for file transfer
						String dir=etlPreferences.getFilesDirectory();
						if(dir.compareTo("")!=0){
							try{
								c.cd(dir);
							}catch(SftpException e){
								loadDataUI.setMessage("The file directory does not exist in this server");
								loadDataUI.setIsLoading(false);
								return;
							}
						}else{
							loadDataUI.setMessage("No file directory is indicated");
							loadDataUI.setIsLoading(false);
							return;
						}
						try{
							try{
								c.mkdir(dataType.getStudy().toString());
							}catch(Exception exist){
								//normal if directory already exists
							}
							c.cd(dataType.getStudy().toString());
							try{
								c.mkdir("clinical");
							}catch(Exception exist2){
								//normal if directory already exists
							}
							c.cd("clinical");
						}catch(SftpException e){
							loadDataUI.setMessage("Directory can not be created");
							loadDataUI.setIsLoading(false);
		            	   	return;
						}

						try{
							for(File f: dataType.getFiles()){
								c.put(f.getAbsolutePath(), ".", null, ChannelSftp.OVERWRITE);
							}
							
						}catch(SftpException e){
							loadDataUI.setMessage("Error when transferring files");
		            	   loadDataUI.setIsLoading(false);
		            	   return;
						}
						
						//run Kettle script
						String fileLoc="";
						try {
							fileLoc=c.pwd();
						} catch (SftpException e) {
							e.printStackTrace();
							loadDataUI.setIsLoading(false);
							return;
						}
						String command="nohup "+etlPreferences.getKettleDirectory()+"/kitchen.sh -norep=Y ";
						command+="-file="+etlPreferences.getJobsDirectory()+"/create_clinical_data.kjb ";
						command+="-param:COLUMN_MAP_FILE="+((ClinicalData)dataType).getCMF().getName()+" ";
						command+="-param:DATA_LOCATION="+fileLoc+"  ";
						
						command+="-param:LOAD_TYPE=I ";	
						if(loadDataUI.getSecurity()){
							command+="-param:SECURITY_REQUIRED=Y ";
						}else{
							command+="-param:SECURITY_REQUIRED=N ";
						}
						String sortPath="";
						try{
							c.cd(etlPreferences.getFilesDirectory()+"/.sort");
							sortPath=etlPreferences.getFilesDirectory()+"/.sort";
						}catch(Exception e){
							try{
								c.cd(etlPreferences.getFilesDirectory());
								c.mkdir(".sort");
								sortPath=etlPreferences.getFilesDirectory()+"/.sort";
							}catch(Exception e2){
								loadDataUI.setMessage("Error when creating sort directory");
								loadDataUI.setIsLoading(false);
								return;
							}
						}
						
						//close streams
						channel.disconnect();
						
						command+="-param:SORT_DIR="+sortPath+" ";
						command+="-param:STUDY_ID="+dataType.getStudy().toString()+" ";
						command+="-param:TOP_NODE='"+dataType.getStudy().getTopNode()+"' ";
						if(((ClinicalData)dataType).getWMF()!=null)	command+="-param:WORD_MAP_FILE="+((ClinicalData)dataType).getWMF().getName();
						
						try{
							channel=session.openChannel("exec");
					      ((ChannelExec)channel).setCommand(command);
						 
					      channel.setInputStream(null);
					      InputStream in=channel.getInputStream();
					 
					      channel.connect();
					 
					      String out="";
					      boolean began=false;
					      byte[] tmp=new byte[1024];
					      boolean running=true;
					      while(running){
					        while(in.available()>0){
					          int i=in.read(tmp, 0, 1024);
					          if(i<0)break;
					          out+=new String(tmp, 0, i);
					          began=true;
					        }
				        	if(began){
				        		Channel channel2=session.openChannel("exec");
							    ((ChannelExec)channel2).setCommand("ps -u "+etlPreferences.getUser()+" -U "+etlPreferences.getUser()+" u");
							    channel2.setInputStream(null);
			      				InputStream in2=channel2.getInputStream();
			      				channel2.connect();
			      				String out2="";
			      				byte[] tmp2=new byte[1024];
			      				while(true){
			      					while(in2.available()>0){
			      						int i=in2.read(tmp2, 0, 1024);
			      						if(i<0)break;
			      						out2+=new String(tmp2, 0, i);
			      					}
			      					if(channel2.isClosed()){
			      						break;
			      					}
							        try{Thread.sleep(1000);}catch(Exception ee){
							        	ee.printStackTrace();
							        }
			      				}
			      				channel2.disconnect();
			      				 Pattern pattern=Pattern.compile(".*create_clinical_data.*", Pattern.DOTALL);
							     Matcher matcher=pattern.matcher(out2);
							     if(!matcher.matches()){
							    	 running=false;
							     }
				        	}
					        try{Thread.sleep(1000);}
					        catch(Exception ee){
					        	ee.printStackTrace();
					        }
				        }
					      
					      Pattern pattern=Pattern.compile(".*Finished job entry \\[run i2b2_load_clinical_data\\] \\(result=\\[true\\]\\).*", Pattern.DOTALL);
					      Matcher matcher=pattern.matcher(out);
					      if(matcher.matches()){
								String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
								Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
								Statement stmt = con.createStatement();
								
								//remove rows for study before adding new ones
								ResultSet rs=stmt.executeQuery("select max(JOB_ID) from CZ_JOB_AUDIT where STEP_DESC like 'Start i2b2_load_clinical_data%'");
								int jobId;
								if(rs.next()){
									jobId=rs.getInt("max(JOB_ID)");
								}
								else{
									con.close();
									loadDataUI.setMessage("Job identifier does not exist");
									loadDataUI.setIsLoading(false);
									return;
								}
								
								out+="\nOracle job id:\n"+String.valueOf(jobId);
								con.close();
							}
							writeLog(out);
							
					      channel.disconnect();
					      session.disconnect();
					    }
					    catch(Exception e){
					    	e.printStackTrace();
							loadDataUI.setMessage("Error when executing Kettle command");
							loadDataUI.setIsLoading(false);
							return;
					    }
						
						session.disconnect();
						channel.disconnect();
					
					}catch (Exception e1){
						e1.printStackTrace();
						loadDataUI.setMessage("Error when transferring files");
						loadDataUI.setIsLoading(false);
						return;
					}
					
					loadDataUI.setIsLoading(false);
				}
			}
		}.start();
		this.loadDataUI.waitForThread();
		this.loadDataUI.displayMessage("Loading process is over.\n Please check monitoring step.");
		WorkPart.updateSteps();
		WorkPart.updateFiles();
	}
	/**
	 *Write the given string in the log file
	 */	
	public void writeLog(String text)
	{
		File log=new File(this.dataType.getPath()+File.separator+"kettle.log");
		try
		{
			FileWriter fw = new FileWriter(log);
			BufferedWriter output = new BufferedWriter(fw);
			output.write(text);		
			output.close();
			((ClinicalData)this.dataType).setLogFile(log);
		}
		catch(IOException ioe){
			this.loadDataUI.displayMessage("File error: "+ioe.getLocalizedMessage());
			ioe.printStackTrace();
		}
	}
	public static void displayMessage(String message){
	    int style = SWT.ICON_INFORMATION | SWT.OK;
	    MessageBox messageBox = new MessageBox(new Shell(), style);
	    messageBox.setMessage(message);
	    messageBox.open();
	}
}
