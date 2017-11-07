/*
 * Copyright (c) 2017  The Hyve B.V.
 *  This file is distributed under the GNU General Public License
 *  (see accompanying file LICENSE).
 */

package org.transmartproject.copy.table

import groovy.transform.CompileStatic
import groovy.transform.Immutable
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.transmartproject.copy.Database
import org.transmartproject.copy.Util
import org.transmartproject.copy.exception.InvalidInput

@Slf4j
@CompileStatic
class Tags {

    @CompileStatic
    @Immutable
    static class TagKey {
        String path
        String tagType
        int index

        @Override
        String toString() {
            "(${path}, ${tagType}, ${index})"
        }
    }

    static final String table = 'i2b2metadata.i2b2_tags'
    static final String tags_file = 'i2b2metadata/i2b2_tags.tsv'

    final Database database

    final LinkedHashMap<String, Class> columns

    final TreeNodes treeNodes

    final Map<TagKey, Long> tags = [:]


    Tags(Database database, TreeNodes treeNodes) {
        this.database = database
        this.treeNodes = treeNodes
        this.columns = this.database.getColumnMetadata(table)
    }

    void fetch() {
        def tagEntries = database.sql.rows(
                "select tag_id, path, tag_type, tags_idx from ${table}".toString()
        ).collectEntries {
            def id = it['tag_id'] as Long
            def path = it['path'] as String
            def tagType = it['tag_type'] as String
            def index = it['tags_idx'] as int
            def key = new TagKey(path: path, tagType: tagType, index: index)
            [(key): id]
        }
        tags.putAll(tagEntries)
        log.info "Tags loaded: ${tags.size()}."
    }

    void load(String rootPath) {
        def tagsFile = new File(rootPath, tags_file)
        if (!tagsFile.exists()) {
            log.info "Skip loading of tags. No file ${tags_file} found."
            return
        }
        tagsFile.withReader { reader ->
            log.info "Reading tags from file ..."
            def insertCount = 0
            def existingCount = 0
            def tsvReader = Util.tsvReader(reader)
            tsvReader.eachWithIndex { String[] data, int i ->
                if (i == 0) {
                    Util.verifyHeader(tags_file, data, columns)
                    return
                }
                try {
                    def tagData = Util.asMap(columns, data)
                    def path = tagData['path'] as String
                    if (!(path in treeNodes.paths)) {
                        throw new InvalidInput("Tag found for non existing tree path ${path}.")
                    } else {
                        def tagType = tagData['tag_type'] as String
                        def index = tagData['tags_idx'] as int
                        def key = new TagKey(path: path, tagType: tagType, index: index)
                        if (tags.containsKey(key)) {
                            existingCount++
                            log.debug "Tag ${key} already present."
                        } else {
                            insertCount++
                            log.info "Inserting new tag ${key} ..."
                            Long id = database.insertEntry(table, columns, 'tag_id', tagData)
                            tags.put(key, id)
                        }
                    }
                } catch(Exception e) {
                    log.error "Error on line ${i} of ${tags_file}: ${e.message}."
                    throw e
                }
            }
            log.info "${existingCount} existing tags found."
            log.info "${insertCount} tags inserted."
        }
    }

}
