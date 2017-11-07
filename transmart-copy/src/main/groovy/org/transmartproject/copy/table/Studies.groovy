/*
 * Copyright (c) 2017  The Hyve B.V.
 *  This file is distributed under the GNU General Public License
 *  (see accompanying file LICENSE).
 */

package org.transmartproject.copy.table

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.transmartproject.copy.Database
import org.transmartproject.copy.Util
import org.transmartproject.copy.exception.InvalidInput
import org.transmartproject.copy.exception.InvalidState

@Slf4j
@CompileStatic
class Studies {

    static final String study_table = 'i2b2demodata.study'
    static final String studies_file = 'i2b2demodata/study.tsv'
    static final String trial_visit_table = 'i2b2demodata.trial_visit_dimension'
    static final String trial_visit_file = 'i2b2demodata/trial_visit_dimension.tsv'
    static final String study_dimensions_table = 'i2b2metadata.study_dimension_descriptions'
    static final String study_dimensions_file = 'i2b2metadata/study_dimension_descriptions.tsv'


    final Database database
    final Dimensions dimensions

    final LinkedHashMap<String, Class> study_columns
    final LinkedHashMap<String, Class> trial_visit_columns
    final LinkedHashMap<String, Class> study_dimensions_columns

    final Map<String, Long> studyIdToStudyNum = [:]
    final List<Long> indexToStudyNum = []
    final List<Long> indexToTrialVisitNum = []

    Studies(Database database, Dimensions dimensions) {
        this.database = database
        this.dimensions = dimensions
        this.study_columns = this.database.getColumnMetadata(study_table)
        this.trial_visit_columns = this.database.getColumnMetadata(trial_visit_table)
        this.study_dimensions_columns = this.database.getColumnMetadata(study_dimensions_table)
    }

    Map findStudy(String studyId) {
        database.sql.firstRow(
                "select * from ${study_table} where study_id = :studyId".toString(),
                [studyId: studyId]
        ) as Map
    }

    List<Long> findTrialVisitNumsForStudy(Long studyNum) {
        database.sql.rows(
                "select trial_visit_num from ${trial_visit_table} where study_num = :studyNum".toString(),
                [studyNum: studyNum]
        ).collect {
            def trialVisit = it as Map
            trialVisit['trial_visit_num'] as Long
        }
    }

    void delete(String studyId) {
        def study = findStudy(studyId)
        if (!study) {
            throw new InvalidState("Study not found: ${studyId}.")
        }
        def studyNum = study['study_num'] as Long
        database.sql.withTransaction {
            log.info "Deleting observations for study ${studyId} ..."
            int observationCount = database.sql.executeUpdate(
                    """delete from ${Observations.table} where trial_visit_num in 
                        (select trial_visit_num from ${trial_visit_table} where study_num = :studyNum)""".toString(),
                    [studyNum: studyNum]
            )
            log.info "${observationCount} observations deleted."

            log.info "Deleting trial visits for study ${studyId} ..."
            int trialVisitCount = database.sql.executeUpdate(
                    "delete from ${trial_visit_table} where study_num = :studyNum".toString(),
                    [studyNum: studyNum]
            )
            log.info "${trialVisitCount} trial visits deleted."

            log.info "Deleting study dimensions for study ${studyId} ..."
            int studyDimensionCount = database.sql.executeUpdate(
                    "delete from ${study_dimensions_table} where study_id = :studyNum".toString(),
                    [studyNum: studyNum]
            )
            log.info "${studyDimensionCount} study dimensions deleted."

            log.info "Deleting tree nodes for study ${studyId} ..."
            int treeNodeCount = database.sql.executeUpdate(
                    "delete from ${TreeNodes.table} where sourcesystem_cd = :studyId".toString(),
                    [studyId: studyId]
            )
            log.info "${treeNodeCount} tree nodes deleted."

            log.info "Deleting study object for study ${studyId} ..."
            database.sql.executeUpdate(
                    "delete from ${study_table} where study_num = :studyNum".toString(),
                    [studyNum: studyNum]
            )
            log.info "Study object deleted."
        }
    }

    void checkIfStudyExists(String studyId) {
        def study = findStudy(studyId)
        if (study) {
            log.error "Found existing study: ${study.toMapString()}."
            log.error "You can delete the study and associated data with: `transmart-copy --delete ${studyId}`."
            throw new InvalidState("Study already exists: ${studyId}.")
        }
        log.info "Study ${studyId} does not exists in the database yet."
    }

    void check(String rootPath) {
        def studiesFile = new File(rootPath, studies_file)
        studiesFile.withReader { reader ->
            def tsvReader = Util.tsvReader(reader)
            tsvReader.eachWithIndex { String[] data, int i ->
                if (i == 0) {
                    Util.verifyHeader(studies_file, data, study_columns)
                    return
                }
                def studyData = Util.asMap(study_columns, data)
                def studyIndex = studyData['study_num'] as long
                if (i != studyIndex + 1) {
                    throw new InvalidInput("The studies ${studies_file} are not in order. (Found ${studyIndex} on line ${i}.)")
                }
                def studyId = studyData['study_id'] as String
                checkIfStudyExists(studyId)
            }
        }
    }

    void clean() {
        def studiesFile = new File(studies_file)
        studiesFile.withReader { reader ->
            def tsvReader = Util.tsvReader(reader)
            tsvReader.eachWithIndex { String[] data, int i ->
                if (i == 0) {
                    Util.verifyHeader(studies_file, data, study_columns)
                    return
                }
                def studyData = Util.asMap(study_columns, data)
                def studyId = studyData['study_id'] as String
                def study = findStudy(studyId)
                if (study) {
                    delete(studyId)
                }
            }
        }
    }

    void load(String rootPath) {
        // Insert study records
        def studiesFile = new File(rootPath, studies_file)
        studiesFile.withReader { reader ->
            def tsvReader = Util.tsvReader(reader)
            database.sql.withTransaction {
                tsvReader.eachWithIndex { String[] data, int i ->
                    if (i == 0) {
                        Util.verifyHeader(studies_file, data, study_columns)
                        return
                    }
                    try {
                        def studyData = Util.asMap(study_columns, data)
                        def studyId = studyData['study_id'] as String
                        def studyNum = database.insertEntry(study_table, study_columns, 'study_num', studyData)
                        log.info "Study ${studyId} inserted [study_num: ${studyNum}]."
                        indexToStudyNum.add(studyNum)
                        studyIdToStudyNum[studyId] = studyNum
                    } catch(Exception e) {
                        log.error "Error on line ${i} of ${studies_file}: ${e.message}."
                        throw e
                    }
                }
            }
        }

        // Insert trial visits
        def trialVisitsFile = new File(rootPath, trial_visit_file)
        trialVisitsFile.withReader { reader ->
            def tsvReader = Util.tsvReader(reader)
            database.sql.withTransaction {
                tsvReader.eachWithIndex { String[] data, int i ->
                    if (i == 0) {
                        Util.verifyHeader(trial_visit_file, data, trial_visit_columns)
                        return
                    }
                    try {
                        def trialVisitData = Util.asMap(trial_visit_columns, data)
                        def studyIndex = trialVisitData['study_num'] as int
                        if (studyIndex >= indexToStudyNum.size()) {
                            throw new InvalidInput("Invalid study index (${studyIndex}). Only ${indexToStudyNum.size()} studies found.")
                        }
                        def studyNum = indexToStudyNum[studyIndex]
                        trialVisitData['study_num'] = studyNum
                        def trialVisitNum = database.insertEntry(trial_visit_table, trial_visit_columns, 'trial_visit_num', trialVisitData)
                        log.info "Trial visit inserted [trial_visit_num: ${trialVisitNum}]."
                        indexToTrialVisitNum.add(trialVisitNum)
                    } catch(Exception e) {
                        log.error "Error on line ${i} of ${trial_visit_file}: ${e.message}."
                        throw e
                    }
                }
            }
        }

        // Insert study dimension descriptions
        def studyDimensionsFile = new File(rootPath, study_dimensions_file)
        studyDimensionsFile.withReader { reader ->
            def tsvReader = Util.tsvReader(reader)
            database.sql.withTransaction {
                tsvReader.eachWithIndex { String[] data, int i ->
                    if (i == 0) {
                        Util.verifyHeader(study_dimensions_file, data, study_dimensions_columns)
                        return
                    }
                    try {
                        def studyDimensionData = Util.asMap(study_dimensions_columns, data)
                        def studyIndex = studyDimensionData['study_id'] as int
                        if (studyIndex >= indexToStudyNum.size()) {
                            throw new InvalidInput("Invalid study index (${studyIndex}). Only ${indexToStudyNum.size()} studies found.")
                        }
                        def studyNum = indexToStudyNum[studyIndex]
                        studyDimensionData['study_id'] = studyNum
                        def dimensionIndex = studyDimensionData['dimension_description_id'] as int
                        if (dimensionIndex >= dimensions.indexToDimensionId.size()) {
                            throw new InvalidInput("Invalid dimension index (${dimensionIndex}). Only ${dimensions.indexToDimensionId.size()} dimensions found.")
                        }
                        def dimensionId = dimensions.indexToDimensionId[dimensionIndex]
                        studyDimensionData['dimension_description_id'] = dimensionId
                        database.insertEntry(study_dimensions_table, study_dimensions_columns, studyDimensionData)
                        log.info "Study dimension inserted [study_id: ${studyNum}, dimension_description_id: ${dimensionId}]."
                    } catch(Exception e) {
                        log.error "Error on line ${i} of ${study_dimensions_file}: ${e.message}."
                        throw e
                    }
                }
            }
        }
    }

}
