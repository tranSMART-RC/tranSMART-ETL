/*************************************************************************
 * tranSMART - translational medicine data mart
 *
 * Copyright 2008-2012 Janssen Research & Development, LLC.
 *
 * This product includes software developed at Janssen Research & Development, LLC.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software  * Foundation, either version 3 of the License, or (at your option) any later version, along with the following terms:
 * 1.	You may convey a work based on this program in accordance with section 5, provided that you retain the above notices.
 * 2.	You may convey verbatim copies of this program code as you receive it, in any medium, provided that you retain the above notices.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS    * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************/
package com.recomdata.pipeline.converter

import groovy.sql.Sql;

import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.recomdata.pipeline.util.Util;

class IlluminaGenotypingFormatter {
	private static final Logger log = Logger.getLogger(IlluminaGenotypingFormatter)
	
		private static Properties props
		private int batchSize
	
		static main(args) {
			String propertiesFile
			String log4jFile
			String dbUrl
			String dbDriver
			String deappUser
			String deappPwd
			String demodataUser
			String demodataPwd
			try {
				propertiesFile = args[0]
				log4jFile = args[1]
				dbUrl = args[2]
				dbDriver = args[3]
				deappUser = args[4]
				deappPwd = args[5]
				demodataUser = args[6]
				demodataPwd = args[7]
			}
			catch (ArrayIndexOutOfBoundsException e){
				println ("There are missing arguments")
				return
			}
			PropertyConfigurator.configure(log4jFile);
	
			IlluminaGenotypingFormatter formatter = new IlluminaGenotypingFormatter()
	
			//if(args.size() > 0){
				//log.info("Start loading property files conf/Common.properties and ${args[0]} ...")
				//formatter.setProperties(Util.loadConfiguration(args[0]));
			//} else {
				log.info("Start loading property files")// conf/Common.properties and conf/Illumina.properties ...")
				formatter.setProperties(Util.loadConfiguration(propertiesFile));
			//}
	
			//Sql deapp = Util.createSqlFromPropertyFile(props, "deapp")
			//Sql i2b2demodata = Util.createSqlFromPropertyFile(props, "i2b2demodata")
				Sql deapp = Sql.newInstance(dbUrl, deappUser, deappPwd, dbDriver)
				Sql i2b2demodata = Sql.newInstance(dbUrl, demodataUser, demodataPwd, dbDriver)
	
			formatter.setBatchSize(Integer.parseInt(props.get("batch_size")))
	
			Map snpMap = formatter.loadSnpList()
			//Util.printMap(snpMap)
	
			formatter.formatGenotype(snpMap)
	
			Map subjectPatientMap = formatter.getSubjectPatientMap()
			//Util.printMap(subjectPatientMap)
	
			Map subjectIdMap = formatter.getSubjectIdMap(deapp)
			Util.printMap(subjectIdMap)
			
			formatter.formatLgen(subjectPatientMap, subjectIdMap)
	
			formatter.createFam(i2b2demodata)
			
			formatter.createPlinkFile(props)
		}	
	
		private void createFam(Sql sql){
	
			if(props.get("skip_create_fam").toString().toLowerCase().equals("yes")){
				log.info "Skip creating FAM file ..."
			}else{
				File fam = new File(props.get("output_fam"))
				if(fam.size() > 0){
					fam.delete()
					fam.createNewFile()
				}
	
				log.info("Start creating FAM file: " + fam.toString())
	
				//StringBuffer sb = new StringBuffer()
	
				String qry = """ select patient_num, decode(lower(sex_cd), 'male', '1', 'female', '2', '9') as gender
						 from i2b2demodata.patient_dimension p, deapp.de_subject_sample_mapping s
						 where s.platform='SNP' and s.trial_name='"""+ props.get("study_id") +"""' and p.patient_num=s.patient_id """
	
				sql.eachRow(qry) {
					println it.patient_num + "\t" + it.gender
					if(!it.patient_num.equals(null)) fam.append(it.patient_num + "\t" + it.patient_num + "\t0\t0\t" + it.gender + "\t9\n" )
				}
	
				//sb.setLength(0)
	
				log.info("End creating FAM file: " + fam.toString())
			}
		}
	
	
		/**
		 *   create subject-patient_num mapping using the mapping file
		 *
		 * @return
		 */
		private Map getSubjectPatientMap() {
	
			Map subjectPatientMap = [:]
			String [] str
	
			File map = new File(props.get("subject_mapping"))
	
			if(map.size() > 0){
				map.eachLine {
					str = it.split("\t")
					subjectPatientMap[str[3].trim()] = str[2].trim()//sample_id : subject id, to put subject id in lgen file
				}
			} else{
				log.error("Subject-patient mapping file: " + map.toString() + " doesn't exist or is empty ...")
			}
			return subjectPatientMap
		}
	
		/**
		 *   create patient_id / patient_num map
		 *
		 * @return
		 */
		private Map getSubjectIdMap(Sql sql) {
	
			Map subjectIdMap = [:]
			String [] str
	
			String qry = """ select patient_id, subject_id
						 from deapp.de_subject_sample_mapping
						 where trial_name='""" + props.get("study_id") +"""'"""

		sql.eachRow(qry) {
			subjectIdMap[it.subject_id]=it.patient_id
		}
	
			return subjectIdMap
		}
	
	
		private void formatLgen(Map subjectPatientMap, Map subjectIdMap){
	
			if(props.get("skip_format_lgen").toString().toLowerCase().equals("yes")){
				log.info "Skip formatting LGEN file ..."
			}else{
	
				String [] str
				//StringBuffer sb = new StringBuffer()
	
				File lgenOutput = new File(props.get("output_lgen_data"))
				File lgenGsmOutput = new File(props.get("output_lgen_gsm_data"))
				if(lgenOutput.size() > 0){
					lgenOutput.delete()
					lgenOutput.createNewFile()
				}
				if(lgenGsmOutput.size() > 0){
					lgenGsmOutput.delete()
					lgenGsmOutput.createNewFile()
				}
	
				log.info("Start formatting LGEN file: " + lgenOutput.toString())
	
				File genotypeInput = new File(props.get("output_genotype_data"))
				if(genotypeInput.size()){
					genotypeInput.eachLine {
						str = it.split("\t")
						//sb.append(subjectPatientMap[str[0]] + "\t" + subjectPatientMap[str[0]] + "\t" + str[1] + "\t" + str[2] + "\n")
						lgenGsmOutput.append(subjectPatientMap[str[0]] + "\t" + subjectIdMap[subjectPatientMap[str[0]]] + "\t" + str[1] + "\t" + str[2] + "\n")
						lgenOutput.append(subjectIdMap[subjectPatientMap[str[0]]] + "\t" + subjectIdMap[subjectPatientMap[str[0]]] + "\t" + str[1] + "\t" + str[2] + "\n")
					}
	
					//lgenOutput.append(sb.toString())
					//sb.setLength(0)
	
					log.info("End formatting LGEN file: " + lgenOutput.toString())
				} else{
					log.error("Genotype data file: " + genotypeInput.toString() + " doesn't exist or is empty ...")
				}
			}
		}
	
	
		private void formatGenotype(Map snpMap){
	
			if(props.get("skip_format_genotype").toString().toLowerCase().equals("yes")){
				log.info "Skip formatting genotype data ..."
			}else{
				String [] str, subjects
				String genotype
				File genotypeInput = new File(props.get("genotype_data"))
	
				//StringBuffer sb = new StringBuffer()
				File genotypeOutput = new File(props.get("output_genotype_data"))
				if(genotypeOutput.size() > 0){
					genotypeOutput.delete()
					genotypeOutput.createNewFile()
				}
	
				log.info("Start creating genotype data file: " + genotypeOutput.toString())
	
				if(genotypeInput.size() > 0){
					genotypeInput.eachLine{
						str = it.split("\t")
						if(it.indexOf("ID_REF") >= 0){
							subjects = str
						} else if(it.indexOf("!") < 0 && it!=""){
							for(int i in 1..str.size()-1) {
								if(str[i].substring(0, 2).compareTo("NC")==0){
									genotype = "0 0" 
								}else{
									genotype = str[i].substring(0, 1) + " " + str[i].substring(1)
								}
								//sb.append(subjects[i].replaceAll("\"", "")  + "\t" + snpMap[str[0].replaceAll("\"", "")]  + "\t" + genotype +  "\n"  )
								genotypeOutput.append(subjects[i].replaceAll("\"", "")  + "\t" + snpMap[str[0].replaceAll("\"", "")]  + "\t" + genotype +  "\n"  )
							}
						}
					}
					//sb.setLength(0)
	
					log.info("End creating genotype data file: " + genotypeOutput.toString())
	
				} else {
					log.error("Genotype file: " + genotypeInput.toString() + " doesn't exist or empty ...")
				}
			}
		}
	
		private Map loadSnpList(){
	
			Map snpIdMap = [:]
			String [] str
	
			File snp = new File(props.get("snp_annotation"))
	
			if(snp.size()>0){
				snp.eachLine {line ->
					if(line.indexOf("SNP_ID") == -1 && line.indexOf("#") == -1){
						str=line.split("\t", -1)
						snpIdMap[str[3]] = str[0]
					}
				}
			} else {
				log.error("SNP list file: " + snp.toString() + " doesn't exist or empty ...")
			}
			return snpIdMap
		}
	
	
		void setBatchSize(int batchSize){
			this.batchSize = batchSize
		}
	
	
		void setProperties(Properties props){
			this.props = props
		}
		void createPlinkFile(Properties props){
			
					if(props.get("skip_plink_file_creation").toString().toLowerCase().equals("yes")){
						log.info "Skip creating PLINK format files ..."
					} else{
						String outputDir = props.get("output_directory")
			
						PlinkConverter pc = new PlinkConverter()
						pc.setPlinkSourceDirectory(outputDir)
						pc.setPlinkDestinationDirectory(outputDir)
						pc.setPlink(props.get("plink"))
						pc.setStudyName(props.get("study_name"))
			
						log.info "Creating Binary PLINK format file ..."
						log.info new Date()
						pc.createBinaryFromLongPlink()
						log.info new Date()
			
						log.info "Creating PLINK format files for each Chromosome ..."
						pc.recodePlinkFileByChrs()
						log.info new Date()
			
						log.info "Recoding Binary PLINK format file ..."
						pc.recodePlinkFile()
						log.info new Date()
					}
				}
}
