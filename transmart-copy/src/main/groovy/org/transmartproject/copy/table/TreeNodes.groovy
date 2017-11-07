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

@Slf4j
@CompileStatic
class TreeNodes {

    static final String table = 'i2b2metadata.i2b2_secure'
    static final String tree_nodes_file = 'i2b2metadata/i2b2_secure.tsv'

    final Database database

    final LinkedHashMap<String, Class> columns

    final Studies studies
    final Concepts concepts

    final Set<String> paths = []

    TreeNodes(Database database, Studies studies, Concepts concepts) {
        this.database = database
        this.studies = studies
        this.concepts = concepts
        this.columns = this.database.getColumnMetadata(table)
    }

    void fetch() {
        def fullNames = database.sql.rows(
                "select c_fullname from ${table}".toString()
        ).collect {
            it['c_fullname'] as String
        }
        paths.addAll(fullNames)
        log.info "Tree nodes loaded: ${paths.size()}."
        log.debug "Paths: ${paths}"
    }

    static final Set<String> operators = ['=', 'like'] as Set

    void validateNode(Map<String, Object> treeNode) {
        def path = (treeNode['c_fullname'] as String)?.trim()
        if (!path.contains('\\')) {
            throw new InvalidInput("Invalid path: ${path}")
        }
        def visualAttributes = treeNode['c_visualattributes'] as String
        def tableName = (treeNode['c_tablename'] as String)?.trim()?.toLowerCase()
        def columnName = (treeNode['c_columnname'] as String)?.trim()?.toLowerCase()
        def operator = (treeNode['c_operator'] as String)?.trim()?.toLowerCase()
        def dimCode = treeNode['c_dimcode'] as String
        if (visualAttributes?.startsWith('C')) {
            log.debug "Container: ${path}"
            return
        }
        if (tableName == 'concept_dimension') {
            if (!(operator in operators)) {
                throw new InvalidInput("Unexpected operator for node ${path}: ${operator}.")
            }
            if (columnName == 'concept_cd') {
                if (!(dimCode in concepts.conceptCodes)) {
                    throw new InvalidInput("Unknown concept code for node ${path}: ${dimCode}.")
                }
            } else if (columnName == 'concept_path') {
                if (!(dimCode in concepts.conceptPaths)) {
                    log.debug "Unknown concept path for node ${path}: ${dimCode}."
                }
            } else {
                throw new InvalidInput("Unexpected column name for concept node ${path}: ${columnName}.")
            }
        } else if (tableName == 'study') {
            if (!(operator in operators)) {
                throw new InvalidInput("Unexpected operator for node ${path}: ${operator}.")
            }
            if (columnName == 'study_id') {
                if (!(dimCode in studies.studyIdToStudyNum.keySet())) {
                    throw new InvalidInput("Unknown study id for node ${path}: ${dimCode}.")
                }
            } else {
                throw new InvalidInput("Unexpected column name for study node ${path}: ${columnName}.")
            }
        } else {
            throw new InvalidInput("Unexpected table name for node ${path}: ${tableName}.")
        }
    }

    void load(String rootPath) {
        def treeNodesFile = new File(rootPath, tree_nodes_file)
        treeNodesFile.withReader { reader ->
            log.info "Reading tree nodes from file ..."
            def insertCount = 0
            def existingCount = 0
            def tsvReader = Util.tsvReader(reader)
            tsvReader.eachWithIndex { String[] data, int i ->
                if (i == 0) {
                    Util.verifyHeader(tree_nodes_file, data, columns)
                    return
                }
                try {
                    def treeNodeData = Util.asMap(columns, data)
                    validateNode(treeNodeData)
                    def path = treeNodeData['c_fullname'] as String
                    if (path in paths) {
                        existingCount++
                        log.debug "Found existing tree node path: ${path}."
                    } else {
                        insertCount++
                        log.info "Inserting new tree node: ${path} ..."
                        database.insertEntry(table, columns, treeNodeData)
                        paths.add(path)
                    }
                } catch(Exception e) {
                    log.error "Error on line ${i} of ${tree_nodes_file}: ${e.message}."
                    throw e
                }
            }
            log.info "${existingCount} existing tree nodes found."
            log.info "${insertCount} tree nodes inserted."
        }
    }

}
