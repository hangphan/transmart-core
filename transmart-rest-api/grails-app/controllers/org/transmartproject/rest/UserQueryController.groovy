package org.transmartproject.rest

import grails.converters.JSON
import groovy.json.JsonSlurper
import org.grails.web.converters.exceptions.ConverterException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.transmartproject.core.exceptions.InvalidArgumentsException
import org.transmartproject.core.userquery.UserQuery
import org.transmartproject.core.userquery.UserQueryResource
import org.transmartproject.rest.misc.CurrentUser

import static org.transmartproject.rest.misc.RequestUtils.checkForUnsupportedParams

class UserQueryController {

    @Autowired
    VersionController versionController

    @Autowired
    CurrentUser currentUser

    @Autowired
    UserQueryResource userQueryResource

    private static final JsonSlurper JSON_SLURPER = new JsonSlurper()

    static responseFormats = ['json']

    def index() {
        List<UserQuery> queries = userQueryResource.list(currentUser)
        respond([queries: queries.collect { toResponseMap(it) }])
    }

    def get(@PathVariable('id') Long id) {
        checkForUnsupportedParams(params, ['id'])
        UserQuery query = userQueryResource.get(id, currentUser)
        respond toResponseMap(query)
    }

    def save(@RequestParam('api_version') String apiVersion) {
        def requestJson = request.JSON as Map
        checkForUnsupportedParams(requestJson, ['name', 'patientsQuery', 'observationsQuery', 'bookmarked',
                                                'subscribed', 'subscriptionFreq'])
        def patientsQueryString = requestJson.patientsQuery?.toString()
        def observationsQueryString = requestJson.observationsQuery?.toString()

        validateQuery(patientsQueryString)
        validateQuery(observationsQueryString)

        UserQuery query = userQueryResource.create(currentUser)
        query.apiVersion = versionController.currentVersion(apiVersion)
        query.with {
            name = requestJson.name
            patientsQuery = patientsQueryString
            observationsQuery = observationsQueryString
            bookmarked = requestJson.bookmarked ?: false
            subscribed = requestJson.subscribed ?: false
            subscriptionFreq = requestJson.subscriptionFreq
        }

        userQueryResource.save(query, currentUser)
        response.status = 201
        respond toResponseMap(query)
    }

    def update(@RequestParam('api_version') String apiVersion,
               @PathVariable('id') Long id) {
        def requestJson = request.JSON as Map
        checkForUnsupportedParams(requestJson, ['name', 'bookmarked', 'subscribed'])
        UserQuery query = userQueryResource.get(id, currentUser)
        if (requestJson.containsKey('name')) {
            query.name = requestJson.name
        }
        if (requestJson.containsKey('bookmarked')) {
            query.bookmarked = requestJson.bookmarked
        }
        if (requestJson.containsKey('subscribed')) {
            query.subscribed = requestJson.subscribed
        }
        userQueryResource.save(query, currentUser)
        response.status = 204
    }

    def delete(@PathVariable('id') Long id) {
        userQueryResource.delete(id, currentUser)
        response.status = 204
    }

    private static Map<String, Object> toResponseMap(UserQuery query) {
        query.with {
            [
                    id               : id,
                    name             : name,
                    patientsQuery    : patientsQuery ? JSON_SLURPER.parseText(patientsQuery) : null,
                    observationsQuery: observationsQuery ? JSON_SLURPER.parseText(observationsQuery) : null,
                    apiVersion       : apiVersion,
                    bookmarked       : bookmarked,
                    subscribed       : subscribed,
                    subscriptionFreq : subscriptionFreq,
                    createDate       : createDate,
                    updateDate       : updateDate,
            ]
        }
    }

    private static void validateQuery(String query) {
        if (query) {
            try {
                JSON.parse(query)
            } catch (ConverterException c) {
                throw new InvalidArgumentsException("Query is not a valid JSON")
            }
        }
    }
}
