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
 *
 ******************************************************************/
  

package com.recomdata.pipeline.annotation

import java.util.Properties;

import groovy.sql.Sql
import org.apache.log4j.Logger

import org.apache.log4j.PropertyConfigurator
import groovy.sql.Sql
import com.recomdata.pipeline.util.Util

class GPLReader {

	private static final Logger log = Logger.getLogger(GPLReader)	
	
	String  sourceDirectory
	Sql sql
	Map expectedProbes
	File snpInfo, probeInfo, snpGeneMap, gplInput, snpMap

	static main(args) {

		PropertyConfigurator.configure("conf/log4j.properties");

		Util util = new Util()
		GPLReader al = new GPLReader()

		Properties props = Util.loadConfiguration("conf/loader.properties")

		Sql deapp = Util.createSqlFromPropertyFile(props, "deapp")
		Sql biomart = Util.createSqlFromPropertyFile(props, "biomart")

		al.loadGxGPL(props, biomart)
	}

	
	void loadGxGPL(Properties props, Sql biomart){

		if(props.get("skip_gx_gpl_loader").toString().toLowerCase().equals("yes")){
			log.info "Skip loading GX GPL annotation file(s) ..."
		}else{


			GexGPL gpl = new GexGPL()
			gpl.setSql(biomart)
			gpl.setAnnotationTable(props.get("annotation_table"))

			if(props.get("recreate_annotation_table").toString().toLowerCase().equals("yes")){
				log.info "Start recreating annotation table ${props.get("annotation_table")} for GPL GX annotation file(s) ..."
				gpl.createAnnotationTable()
			}


			String annotationSourceDirectory = props.get("annotation_source")
			String [] gplList = props.get("gpl_list").split(/\,/)
			gplList.each {
				File annotationSource = new File(annotationSourceDirectory + File.separator + "GPL." + it + ".txt")
				gpl.loadGxGPLs(annotationSource)
			}
		}
	}

	void processGPLs(Properties props){
		String inputFileName=props.get("input_file")
		long numProbes
		if(inputFileName.indexOf(";")){
			String [] names = inputFileName.split(";")
			for(int i in 0..names.size()-1){
				File inputFile = new File(sourceDirectory + File.separator + names[i])

				if(inputFile.exists()){
					log.info("Start parsing " + inputFile.toString())

					setGPLInputFile(inputFile)
					numProbes = parseGPLFile(props) //probeInfo, snpGeneMap, snpMapFile)
					if(numProbes == expectedProbes[names[i]])
						log.info("Probes in " + names[i] + ": " + numProbes + "; expected: " + expectedProbes[names[i]])
					else
						log.warn("Probes in " + names[i] + ": " + numProbes + "; expected: " + expectedProbes[names[i]])
				}else{
					log.warn("Cannot find the file: " + inputFile.toString())
				}
			}
		}else{
			File inputFile = new File(sourceDirectory + File.separator + inputFileName)

			log.info("Start parsing " + inputFile.toString())

			setGPLInputFile(inputFile)
			numProbes = parseGPLFile(props) //probeInfo, snpGeneMap, snpMapFile)
			if(numProbes == expectedProbes[inputFileName])
				log.info("Probes in " + inputFileName + ": " + numProbes + "; expected: " + expectedProbes[inputFileName])
			else
				log.warn("Probes in " + inputFileName + ": " + numProbes + "; expected: " + expectedProbes[inputFileName])
		}
	}


	long parseGPLFile(Properties props){
		String [] str, header
		def genes = [:]
		boolean isHeaderLine = false
		boolean isAnnotationLine = false

		StringBuffer sb_probeinfo = new StringBuffer()
		StringBuffer sb_snpGeneMap = new StringBuffer()
		StringBuffer sb_snpMap = new StringBuffer()

		long numProbes = 0

		//check there is all column numbers in properties
		def c_snpId=props.get("snp_id")
		def c_rsId=props.get("rsId")
		def c_chr=props.get("chr")
		def c_pos=props.get("pos")
		def c_gene=props.get("gene")//optional
		Map columns=[:]
		def hadHeaderLine=false
		if(c_snpId!=null && c_rsId!=null && c_chr!=null && c_pos!=null){
			gplInput.eachLine{
				if(hadHeaderLine) {
					str = it.split("\t")
					String snpId, rsId, chr, pos
					snpId = str[columns["snpId"]]
					rsId = str[columns["rsId"]]
					chr = str[columns["chr"]]
					pos = str[columns["pos"]]
					if(!chr.equals(null) && !pos.equals(null) && (chr.size()>0) && (pos.size() > 0)){
						numProbes++
						if(!rsId.equals(null) && (rsId.indexOf("---") == -1))  sb_probeinfo.append(snpId + "\t" + rsId + "\n")
						sb_snpMap.append(chr + "\t" + snpId + "\t0\t" + pos + "\n")
					}
					if(columns["gene"]!=null){
						for(String g in str[columns["gene"]].split("///", -1)) {
							if(g.compareTo("")!=0){
								sb_snpGeneMap.append(rsId+"\t"+g + "\n")
							}
						}
					}
				}else if(it.indexOf(c_snpId) >= 0 && it.indexOf(c_rsId) >= 0 && it.indexOf(c_chr) >= 0 && it.indexOf(c_pos) >= 0) {
					str = it.split("\t")
					for(int i=0; i<str.size(); i++){
						switch ( str[i] ) {
							case c_snpId:
								columns["snpId"]=i
								break
						
							case c_rsId:
								columns["rsId"]=i
								break
						
							case c_chr:
								columns["chr"]=i
								break
						
							case c_pos:
								columns["pos"]=i
								break
						
							case c_gene:
								columns["gene"]=i
								break
						}
					}
					if(columns["snpId"]!=null && columns["rsId"]!=null && columns["chr"]!=null && columns["pos"]!=null){
						hadHeaderLine = true
						Util.printMap(columns)
					}else{
						log.info ("There is a problem with headers of the annotation file. Annotation cannot eb loaded")
					}
				}
			}
		} else{
			log.info("Annotation file cannot be parsed, all the columns names are not provided")
		}

		probeInfo.append(sb_probeinfo.toString())
		snpGeneMap.append(sb_snpGeneMap.toString())
		snpMap.append(sb_snpMap.toString())

		return numProbes
	}


	Map getSNPGeneMapping(String associatedGene){
		String [] str, gene
		def mapping = [:]

		if(associatedGene.indexOf("///") >= 0) {
			str = associatedGene.split("///")
			for(int i in 0..str.size()-1) {

				if(str[i].indexOf("//")) {
					gene = str[i].split("//")
					if(gene.size() >= 6 &&  !(gene[5].indexOf("---") >= 0)){
						// 4 -- gene symbol; 5 -- gene id; 6 -- gene description
						//println gene[4] + ":" + gene[5] + ":" + gene[6]
						mapping[gene[5].trim()] = gene[4].trim() + ":" + gene[6].trim()
					}
				}
			}
		}
		return mapping
	}


	void setProbeInfo(File probeInfo){
		this.probeInfo = probeInfo
	}

	void setSnpGeneMap(File snpGeneMap){
		this.snpGeneMap = snpGeneMap
	}


	void setSnpMap(File snpMap){
		this.snpMap = snpMap
	}


	void setGPLInputFile(File gplInput){
		this.gplInput = gplInput
	}


	void setSourceDirectory(String sourceDirectory){
		this.sourceDirectory = sourceDirectory
	}

	void setExpectedProbes(Map expectedProbes){
		this.expectedProbes = expectedProbes
	}

	void setSql(Sql sql){
		this.sql = sql
	}
}
