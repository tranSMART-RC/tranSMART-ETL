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
package fr.sanofi.fcl4transmart.controllers.listeners.geneExpression;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.KettleEnvironment;
import org.pentaho.di.core.Result;
import org.pentaho.di.core.logging.CentralLogStore;
import org.pentaho.di.core.logging.Log4jBufferAppender;
import org.pentaho.di.job.Job;
import org.pentaho.di.job.JobMeta;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import fr.sanofi.fcl4transmart.handlers.PreferencesHandler;
import fr.sanofi.fcl4transmart.handlers.etlPreferences;
import fr.sanofi.fcl4transmart.model.classes.dataType.GeneExpressionData;
import fr.sanofi.fcl4transmart.model.classes.workUI.geneExpression.LoadDataUI;
import fr.sanofi.fcl4transmart.model.interfaces.DataTypeItf;
import fr.sanofi.fcl4transmart.ui.parts.WorkPart;
/**
 *This class controls the gene expression data loading step
 */	
public class LoadGeneExpressionDataListener implements Listener{
	private DataTypeItf dataType;
	private LoadDataUI loadDataUI;
	private String topNode;
	private String path;
	private String sortName;
	private String messageException;
	private ChannelSftp c;
	public LoadGeneExpressionDataListener(LoadDataUI loadDataUI, DataTypeItf dataType){
		this.dataType=dataType;
		this.loadDataUI=loadDataUI;
	}
	/**
	 *Loads the gene expression data:
	 *-initiate Kettle environment
	 *-Find Kettle files
	 *-Set Kettle parameters
	 *-Calls the Kettle job
	 *-Save the log file
	 */	
	@Override
	public void handleEvent(Event event) {
		this.topNode=this.loadDataUI.getTopNode();
		this.path=this.dataType.getPath().getAbsolutePath();
		this.sortName=this.dataType.getStudy().getPath().getParentFile().getAbsolutePath()+File.separator+".sort";
		loadDataUI.openLoadingShell();
		new Thread(){
			public void run() {
				if(!loadDataUI.getEtlServer()){
					try {
						String[] splited=topNode.split("\\\\", -1);
						if(splited[0].compareTo("")!=0){
							loadDataUI.setMessage("A study node has to begin by the character '\\'");
							loadDataUI.setIsLoading(false);
							return;
						}
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
								stmt.executeQuery("insert into i2b2 values(0, '\\"+splited[1]+"\\', '"+splited[1]+"','N','CA',0,null, null, 'CONCEPT_CD','CONCEPT_DIMENSION','CONCEPT_PATH', 'T', 'LIKE','\\"+splited[1]+"\\', null, '\\"+splited[1]+"\\', sysdate, null, null, null, null, null, '@', null, null, null)");
							}
							con.close();
						}catch(SQLException e){
							e.printStackTrace();
							loadDataUI.displayMessage("SQL error: "+e.getLocalizedMessage());
							loadDataUI.setIsLoading(false);
							return;
						} catch (ClassNotFoundException e) {
							loadDataUI.displayMessage("Java error: Class not found exception");
							e.printStackTrace();
							loadDataUI.setIsLoading(false);
							return;
						}
						//initiate kettle environment
						URL kettleUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/lib/pentaho");
						kettleUrl = FileLocator.toFileURL(kettleUrl);  
						System.setProperty("KETTLE_PLUGIN_BASE_FOLDERS", kettleUrl.getPath());
						KettleEnvironment.init(false);
						
						//find the kettle job to initiate the loading
						URL jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/load_gene_expression_data.kjb");
						jobUrl = FileLocator.toFileURL(jobUrl);  
						String jobPath = jobUrl.getPath();
						//create a new job from the kettle file
						JobMeta jobMeta = new JobMeta(jobPath, null);
						Job job = new Job(null, jobMeta);		
						
						//find the other files needed for this job and put them in the cache
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/validate_gene_expression_params.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/validate_gene_expression_columns.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/check_gene_expression_filenames.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/load_all_gene_expression_files_for_study.kjb");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/run_i2b2_process_mrna_data.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/load_subject_sample_map_to_lt.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/get_list_of_gene_expression_filenames.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/load_gene_expression_one_study.kjb");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/set_gene_expression_filename.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/validate_gene_expression_columns.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/load_gene_expression_data_to_lz.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/pivot_gene_file.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/cz_end_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/cz_start_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/cz_write_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl);
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/write_study_id_to_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						jobUrl = new URL("platform:/plugin/fr.sanofi.fcl4transmart/jobs_kettle/write_gene_expression_audit.ktr");
						jobUrl = FileLocator.toFileURL(jobUrl); 
						
						job.getJobMeta().setParameterValue("DATA_FILE_PREFIX", "raw");
						job.getJobMeta().setParameterValue("DATA_LOCATION", path);
						job.getJobMeta().setParameterValue("MAP_FILENAME", ((GeneExpressionData)dataType).getStsmf().getName());
						job.getJobMeta().setParameterValue("DATA_TYPE","L");
						job.getJobMeta().setParameterValue("FilePivot_LOCATION","");
						job.getJobMeta().setParameterValue("LOAD_TYPE", "I");
						job.getJobMeta().setParameterValue("SAMPLE_REMAP_FILENAME", "NOSAMPLEREMAP");
						job.getJobMeta().setParameterValue("SAMPLE_SUFFIX", ".rma-Signal");
						
						if(loadDataUI.getSecurity()){
							job.getJobMeta().setParameterValue("SECURITY_REQUIRED", "Y");
						}else{
							job.getJobMeta().setParameterValue("SECURITY_REQUIRED", "N");
						}
						job.getJobMeta().setParameterValue("SOURCE_CD", "STD");
			
						File sort=new File(sortName);
						if(!sort.exists()){
							FileUtils.forceMkdir(sort);
						}
						job.getJobMeta().setParameterValue("SORT_DIR", sortName);
						
						job.getJobMeta().setParameterValue("STUDY_ID", dataType.getStudy().toString());
						job.getJobMeta().setParameterValue("TOP_NODE", topNode);
			
						job.getJobMeta().setParameterValue("TM_CZ_DB_SERVER", PreferencesHandler.getDbServer());
						job.getJobMeta().setParameterValue("TM_CZ_DB_NAME", PreferencesHandler.getDbName());
						job.getJobMeta().setParameterValue("TM_CZ_DB_PORT", PreferencesHandler.getDbPort());
						job.getJobMeta().setParameterValue("TM_CZ_DB_USER", PreferencesHandler.getTm_czUser());
						job.getJobMeta().setParameterValue("TM_CZ_DB_PWD", PreferencesHandler.getTm_czPwd());
						job.getJobMeta().setParameterValue("TM_LZ_DB_SERVER",PreferencesHandler.getDbServer());
						job.getJobMeta().setParameterValue("TM_LZ_DB_NAME", PreferencesHandler.getDbName());
						job.getJobMeta().setParameterValue("TM_LZ_DB_PORT", PreferencesHandler.getDbPort());
						job.getJobMeta().setParameterValue("TM_LZ_DB_USER", PreferencesHandler.getTm_lzUser());
						job.getJobMeta().setParameterValue("TM_LZ_DB_PWD", PreferencesHandler.getTm_lzPwd());
						job.getJobMeta().setParameterValue("DEAPP_DB_SERVER",PreferencesHandler.getDbServer());
						job.getJobMeta().setParameterValue("DEAPP_DB_NAME", PreferencesHandler.getDbName());
						job.getJobMeta().setParameterValue("DEAPP_DB_PORT", PreferencesHandler.getDbPort());
						job.getJobMeta().setParameterValue("DEAPP_DB_USER", PreferencesHandler.getDeappUser());
						job.getJobMeta().setParameterValue("DEAPP_DB_PWD", PreferencesHandler.getDeappPwd());
						
						if(loadDataUI.getIndexes()){
							//drop indexes
							String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
							Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
							
							String sql = "{call i2b2_mrna_index_maint(?)}";
							CallableStatement call = con.prepareCall(sql);
							call.setString(1,"DROP");
							call.executeUpdate();
	
							con.close();
						}
						
						job.start();
						
						//wait for kettle to finish
						boolean kettleFinished=false;
						
						while(!job.isFinished() && !kettleFinished){
							Log4jBufferAppender appender = CentralLogStore.getAppender();
							String logText = appender.getBuffer(job.getLogChannelId(), false).toString();
							Pattern pattern=Pattern.compile(".*Starting entry \\[run i2b2_process_mrna_data\\].*", Pattern.DOTALL);
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
							ResultSet rs=stmt.executeQuery("select max(JOB_ID) from CZ_JOB_AUDIT where STEP_DESC='Starting i2b2_process_mrna_data'");
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
								rs=stmt.executeQuery("select * from cz_job_audit where (step_desc like '%End i2b2_process_mrna_data%' or step_status='FAIL') and job_id="+jobId);
								if(rs.next()){
									procedureFinished=true;
								}
								job.waitUntilFinished(60*1000);
							}
							con.close();
						}						
						
						@SuppressWarnings("unused")
						Result result = job.getResult();
						Display.getDefault().asyncExec(new Runnable() {
				            public void run() {
								loadDataUI.displayMessage("Loading process is over.\n Please check monitoring step.");
				            }
						});
						
						Log4jBufferAppender appender = CentralLogStore.getAppender();
						String logText = appender.getBuffer(job.getLogChannelId(), false).toString();
						
						Pattern pattern=Pattern.compile(".*Finished job entry \\[run i2b2_process_mrna_data\\].*", Pattern.DOTALL);
						Matcher matcher=pattern.matcher(logText);
						if(matcher.matches()){
							String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
							Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
							Statement stmt = con.createStatement();
							
							//remove rows for this study before adding new ones
							ResultSet rs=stmt.executeQuery("select max(JOB_ID) from CZ_JOB_AUDIT where STEP_DESC='Starting i2b2_process_mrna_data'");
							int jobId;
							if(rs.next()){
								jobId=rs.getInt("max(JOB_ID)");
							}
							else{
								con.close();
								loadDataUI.setIsLoading(false);
								return;
							}
							
							logText+="\nOracle job id:\n"+String.valueOf(jobId);
							con.close();
						}
						
						writeLog(logText);
						CentralLogStore.discardLines(job.getLogChannelId(), false);
						
						if(loadDataUI.getIndexes()){
							//add indexes
							String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
							Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
							
							String sql =  "begin i2b2_mrna_index_maint(?); end;" ; // stored proc
							CallableStatement call = con.prepareCall(sql);
							call.setQueryTimeout(3600);//time out if no answer after one hour
							call.setString(1,"ADD");
							try{
								call.executeUpdate();
							}catch(SQLException e){
								e.printStackTrace();
							}
							
							con.close();
						}
						
					} 
					catch (Exception e1) {
						messageException=e1.getLocalizedMessage();
						Display.getDefault().asyncExec(new Runnable() {
				            public void run() {
								loadDataUI.displayMessage("Error: "+messageException);
							}
						});
						
						loadDataUI.setIsLoading(false);
						e1.printStackTrace();
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
								c.mkdir("gene");
							}catch(Exception exist2){
								//normal if directory already exists
							}
							c.cd("gene");
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
						
						//drop indexes if needed
						if(loadDataUI.getIndexes()){
							String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
							Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
							
							String sql = "{call i2b2_mrna_index_maint(?)}";
							CallableStatement call = con.prepareCall(sql);
							call.setString(1,"DROP");
							call.executeUpdate();
	
							con.close();
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
						command+="-file="+etlPreferences.getJobsDirectory()+"/load_gene_expression_data.kjb ";
						command+="-param:DATA_FILE_PREFIX=raw ";
						command+="-param:DATA_LOCATION="+fileLoc+" ";
						command+="-param:DATA_TYPE=R ";
						command+="-param:FilePivot_LOCATION="+etlPreferences.getJobsDirectory()+" ";
						command+="-param:LOAD_TYPE=I ";
						command+="-param:LOG_BASE=2 ";
						command+="-param:MAP_FILENAME="+((GeneExpressionData)dataType).getStsmf().getName()+" ";
						command+="-param:SAMPLE_REMAP_FILENAME=NOSAMPLEREMAP ";
						command+="-param:SAMPLE_SUFFIX=.rma-Signal ";
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
						command+="-param:SORT_DIR="+sortPath+" ";
						command+="-param:SOURCE_CD=STD ";
						command+="-param:STUDY_ID="+dataType.getStudy().toString()+" ";
						command+="-param:TOP_NODE='"+dataType.getStudy().getTopNode()+"' ";
						command+="-param:JAVA_HOME=$JAVA_HOME";
						
						try{
							channel=session.openChannel("exec");
					      ((ChannelExec)channel).setCommand(command);
						 
					      channel.setInputStream(null);
					      					 
					      InputStream in=channel.getInputStream();
					 
					      channel.connect();
					 
					      String out="";
					      byte[] tmp=new byte[1024];
					      boolean running=true;
					      boolean began=false;
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
			      				 Pattern pattern=Pattern.compile(".*load_gene_expression.*", Pattern.DOTALL);
							     Matcher matcher=pattern.matcher(out2);
							     if(!matcher.matches()){
							    	 running=false;
							     }
				        	}
					        try{Thread.sleep(1000);}catch(Exception ee){}
					      }
					      
					      //re-add indexes if needed
					      if(loadDataUI.getIndexes()){
								//add indexes
								String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
								Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
								
								String sql =  "begin i2b2_mrna_index_maint(?); end;" ; // stored proc
								CallableStatement call = con.prepareCall(sql);
								call.setQueryTimeout(3600);//time out if no answer after one hour
								call.setString(1,"ADD");
								try{
									call.executeUpdate();
								}catch(SQLException e){
									e.printStackTrace();
								}
								
								con.close();
							}
					      
					      Pattern pattern=Pattern.compile(".*Finished job entry \\[run i2b2_process_mrna_data\\].*", Pattern.DOTALL);
							Matcher matcher=pattern.matcher(out);
							if(matcher.matches()){
								String connectionString="jdbc:oracle:thin:@"+PreferencesHandler.getDbServer()+":"+PreferencesHandler.getDbPort()+":"+PreferencesHandler.getDbName();
								Connection con = DriverManager.getConnection(connectionString, PreferencesHandler.getTm_czUser(), PreferencesHandler.getTm_czPwd());
								Statement stmt = con.createStatement();
								
								//remove rows for this study before adding new ones
								ResultSet rs=stmt.executeQuery("select max(JOB_ID) from CZ_JOB_AUDIT where STEP_DESC='Starting i2b2_process_mrna_data'");
								int jobId;
								if(rs.next()){
									jobId=rs.getInt("max(JOB_ID)");
								}
								else{
									con.close();
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
	 *Writes a string corresponding to Kettle log in a log file
	 */			
	public void write(String text)
	{
		FileDialog fd=new FileDialog(new Shell());
		fd.setText("Choose a log file");
		String filePath = fd.open();
		try
		{
			FileWriter fw = new FileWriter(filePath, true);
			BufferedWriter output = new BufferedWriter(fw);
			output.write(text);
			output.flush();			
			output.close();
		}
		catch(IOException ioe){
			loadDataUI.displayMessage("File error: "+ioe.getLocalizedMessage());
			ioe.printStackTrace();
			}
	}
	public void writeLog(String text)
	{
		File log=new File(dataType.getPath()+File.separator+"kettle.log");
		try
		{
			FileWriter fw = new FileWriter(log);
			BufferedWriter output = new BufferedWriter(fw);
			output.write(text);		
			output.close();
			((GeneExpressionData)dataType).setLogFile(log);
		}
		catch(IOException ioe){
			loadDataUI.setMessage("File error: "+ioe.getLocalizedMessage());
			ioe.printStackTrace();
		}
	}
}
