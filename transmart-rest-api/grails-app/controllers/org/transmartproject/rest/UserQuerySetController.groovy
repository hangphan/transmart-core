package org.transmartproject.rest

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.transmartproject.core.userquery.UserQuerySet
import org.transmartproject.core.userquery.UserQuerySetResource
import org.transmartproject.rest.misc.CurrentUser

import static org.transmartproject.rest.misc.RequestUtils.checkForUnsupportedParams

class UserQuerySetController {

    @Autowired
    CurrentUser currentUser

    @Autowired
    UserQuerySetResource userQuerySetResource

    static responseFormats = ['json']

    /**
     * Scans for changes in results of the stored queries and updates stored sets:
     * <code>/${apiVersion}/queries/sets/scan</code>
     *
     * This endpoint should be called after loading, deleting or updating the data.
     * Only available for administrators.
     *
     * @return number of sets that were updated, which is also a number of created querySets
     */
    def scan() {
        Integer result = userQuerySetResource.scan(currentUser)
        response.status = 201
        respond([numberOfUpdatedSets: result])
    }

    /**
     * Gets a list of query sets related to a specific query:
     * <code>/${apiVersion}/queries/${queryId}/sets</code>
     *
     * @param maxNumberOfDiffs - max number of returned diffs per set
     * @param maxNumberOfSets - max number of returned sets
     * @param queryId - id of the query
     * @return list of querySets
     */
    def getSetsByQueryId(@PathVariable('queryId') Long queryId) {
        checkForUnsupportedParams(params, ['queryId', 'maxNumberOfDiffs', 'maxNumberOfSets'])
        def maxNumberOfDiffs = params.maxNumberOfDiffs as Integer
        def maxNumberOfSets = params.maxNumberOfSets as Integer

        List<UserQuerySet> querySets = userQuerySetResource.getQuerySetsByQueryId(queryId, currentUser,
                maxNumberOfSets)
        respond respond([querySets: querySets.collect { toResponseMap(it) }])
    }

    /**
     * Gets a list of query result change entries by query id - history of data sets changes for specific query:
     * <code>/${apiVersion}/queries/${queryId}/sets/diffs</code>
     *
     * @param maxNumberOfDiffs - max number of returned diffs per set
     * @param maxNumberOfSets - max number of returned sets
     * @param queryId - id of the query
     * @return list of queryDiffs
     */
    def getDiffsByQueryId(@PathVariable('queryId') Long queryId) {
        checkForUnsupportedParams(params, ['queryId', 'maxNumberOfDiffs', 'maxNumberOfSets'])
        def maxNumberOfDiffs = params.maxNumberOfDiffs as Integer
        def maxNumberOfSets = params.maxNumberOfSets as Integer

        List<UserQuerySet> querySets = userQuerySetResource.getQuerySetsByQueryId(queryId, currentUser,
                maxNumberOfSets)
        respond respond([querySets: querySets.collect { toResponseMapWithDiffs(it) }])
    }

    private static Map<String, Object> toResponseMapWithDiffs(UserQuerySet querySet) {
        querySet.with {
            [
                    id               : it.id,
                    setSize          : it.setSize,
                    setType          : it.setType,
                    createDate       : it.createDate,
                    diffs            : it.querySetDiffs.collect{ diff ->
                        [
                                id        : diff.id,
                                objectId  : diff.objectId,
                                changeFlag: diff.changeFlag
                        ] }
            ]
        }
    }

    private static Map<String, Object> toResponseMap(UserQuerySet querySet) {
        querySet.with {
            [
                    id               : it.id,
                    setSize          : it.setSize,
                    setType          : it.setType,
                    createDate       : it.createDate,
                    instances        : it.querySetInstances.collect{ diff ->
                        [
                                id        : diff.id,
                                objectId  : diff.objectId,
                        ] }
            ]
        }
    }

}
