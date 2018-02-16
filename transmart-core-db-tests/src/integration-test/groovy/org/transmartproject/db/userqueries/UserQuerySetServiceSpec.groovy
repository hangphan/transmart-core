package org.transmartproject.db.userqueries

import grails.test.mixin.integration.Integration
import grails.transaction.Rollback
import org.hibernate.SessionFactory
import org.transmartproject.core.exceptions.AccessDeniedException
import org.transmartproject.core.userquery.ChangeFlag
import org.transmartproject.db.TransmartSpecification
import org.transmartproject.db.querytool.QuerySet
import org.transmartproject.db.querytool.QuerySetDiff

@Integration
@Rollback
class UserQuerySetServiceSpec extends TransmartSpecification {


    UserQuerySetService userQuerySetService

    SessionFactory sessionFactory

    UserQueryTestData userQueryTestData

    void setupData() {
        userQueryTestData = UserQueryTestData.createDefault()
        userQueryTestData.saveAll()
        sessionFactory.currentSession.flush()
    }

    void "test fetching querySets by a query id"() {
        setupData()

        when:
        def user = userQueryTestData.user
        def result = userQuerySetService.getQuerySetsByQueryId(userQueryTestData.queries[0].id, user, 999)

        then:
        result != null

        // check querySetInstances
        result.size() == 1
        result.querySetInstances.containsAll(userQueryTestData.querySetInstances[0], userQueryTestData.querySetInstances[1])

        // check querySets
        (result as Set).size() == 2
        result.containsAll(userQueryTestData.querySets[0])

        // check query
        (result.query as Set).size() == 1
        result.query.contains(userQueryTestData.queries[0])

    }

    void "test scanning for query set changes"() {
        setupData()

        when:
        // user is not admin
        def user = userQueryTestData.user
        def result = userQuerySetService.scan(user)

        then:
        AccessDeniedException ex = thrown()
        ex.message == 'Only allowed for administrators.'

        when:
        // user is not admin
        def adminUser = userQueryTestData.adminUser
        result = userQuerySetService.scan(adminUser)
        def createdSetsForFirstQuery = userQuerySetService.getQuerySetsByQueryId(userQueryTestData.queries[0].id,
                user, 999)

        then:
        result != null
        //check query_set_diffs entries
        result == 2
        createdSetsForFirstQuery.size() == 4
        createdSetsForFirstQuery.findAll { it.querySetDiffs.changeFlag == ChangeFlag.ADDED }.size() == 3
        createdSetsForFirstQuery.findAll { it.changeFlag == ChangeFlag.REMOVED }.size() == 1

        //check number of added entries in total
        QuerySet.list().size() == userQueryTestData.querySets.size() + result //old set entries + entries created by scan
        QuerySetDiff.list().size() == 2 * createdSetsForFirstQuery.size()    //both sets are the same
    }

}



