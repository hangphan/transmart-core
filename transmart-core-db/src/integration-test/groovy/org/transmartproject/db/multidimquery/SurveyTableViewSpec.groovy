package org.transmartproject.db.multidimquery

import grails.test.mixin.integration.Integration
import grails.transaction.Rollback
import org.springframework.beans.factory.annotation.Autowired
import org.transmartproject.core.multidimquery.Hypercube
import org.transmartproject.core.multidimquery.MultiDimensionalDataResource
import org.transmartproject.db.multidimquery.query.Constraint
import org.transmartproject.db.multidimquery.query.StudyNameConstraint
import org.transmartproject.db.user.User
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.containsString
import static org.transmartproject.core.ontology.VariableDataType.NUMERIC
import static org.transmartproject.core.ontology.VariableDataType.STRING
import static org.transmartproject.core.ontology.VariableDataType.DATE
import static org.transmartproject.core.ontology.Measure.NOMINAL
import static org.transmartproject.core.ontology.Measure.SCALE
import static spock.util.matcher.HamcrestSupport.that

@Rollback
@Integration
class SurveyTableViewSpec extends Specification {

    @Autowired
    MultiDimensionalDataResource multiDimService

    private final UTC = TimeZone.getTimeZone('UTC')

    def 'survey 1'() {
        setup:
        def user = User.findByUsername('test-public-user-1')
        Constraint constraint = new StudyNameConstraint(studyId: "SURVEY1")
        Hypercube hypercube = multiDimService.retrieveClinicalData(constraint, user, [DimensionImpl.PATIENT])

        when:
        def transformedView = new SurveyTableView(hypercube)
        then: 'header matches expectations'
        def columns = transformedView.indicesList
        columns*.label == ['FISNumber', 'birthdate1', 'birthdate1.date', 'favouritebook', 'favouritebook.date', 'gender1', 'gender1.date']
        then: 'columns metadata matches expectations'
        def metadata = columns*.metadata
        metadata*.type == [NUMERIC, DATE, DATE, STRING, DATE, NUMERIC, DATE]
        metadata*.measure == [SCALE, SCALE, SCALE, NOMINAL, SCALE, NOMINAL, SCALE]
        metadata*.description == ['FIS Number', 'Birth Date', 'Date of measurement', 'Favourite Book', 'Date of measurement', 'Gender', 'Date of measurement']
        metadata*.width == [12, 22, 22, 400, 22, 12, 22]
        metadata*.decimals == [0, null, null, null, null, null, null]
        metadata*.columns == [12, 22, 22, 400, 22, 14, 22]
        def height1Metadata = columns.find { it.label == 'gender1' }.metadata
        height1Metadata.valueLabels == [(new BigDecimal(1)): 'Female', (new BigDecimal(2)): 'Male', (new BigDecimal(-2)): 'Not Specified']
        height1Metadata.missingValues.values == [new BigDecimal(-2)]

        when: 'get row'
        def rows = transformedView.rows.toList()
        then: 'content matches expectations'
        rows[0][columns[0]] == '123457'
        rows[0][columns[1]] == Date.parse('yyyy-MM-dd hh:mm:ss', '1986-10-22 00:00:00', UTC)
        rows[0][columns[2]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2010-12-16 20:23:15')
        that rows[0][columns[3]] as String, containsString('Dostoyevsky')
        rows[0][columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2016-03-21 10:36:01')
        rows[0][columns[5]] == -2
        rows[0][columns[6]] == null
        rows[1][columns[0]] == '123456'
        rows[1][columns[1]] == Date.parse('yyyy-MM-dd hh:mm:ss', '1980-08-12 00:00:00', UTC)
        rows[1][columns[2]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2015-11-14 19:05:00')
        that rows[1][columns[3]] as String, containsString('Hofstadter')
        rows[1][columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2016-03-21 10:36:01')
        rows[1][columns[5]] == 2
        rows[1][columns[6]] == null

        cleanup:
        if(transformedView) transformedView.close()
    }

    def 'survey 2'() {
        setup:
        def user = User.findByUsername('test-public-user-1')
        Constraint constraint = new StudyNameConstraint(studyId: "SURVEY2")
        Hypercube hypercube = multiDimService.retrieveClinicalData(constraint, user, [DimensionImpl.PATIENT])

        when:
        def transformedView = new SurveyTableView(hypercube)
        then: 'header matches expectations'
        def columns = transformedView.indicesList
        columns*.label == ['FISNumber', 'description', 'description.date', 'height1', 'height1.date']
        then: 'columns metadata matches expectations'
        def metadata = columns*.metadata
        metadata*.type == [NUMERIC, STRING, DATE, NUMERIC, DATE]
        metadata*.measure == [SCALE, NOMINAL, SCALE, SCALE, SCALE]
        metadata*.description == ['FIS Number', 'Description', 'Date of measurement', 'Height', 'Date of measurement']
        metadata*.width == [12, 200, 22, 14, 22]
        metadata*.decimals == [0, null, null, 2, null]
        metadata*.columns == [12, 210, 22, 15, 22]
        def height1Metadata = columns.find { it.label == 'height1' }.metadata
        height1Metadata.valueLabels == [(new BigDecimal(-1)): 'Asked, but not answered']
        height1Metadata.missingValues.values == [new BigDecimal(-1)]

        when: 'get row'
        def rows = transformedView.rows.toList()
        then: 'content matches expactations'
        rows[0][columns[0]] == '123457'
        rows[0][columns[1]] == 'No description'
        rows[0][columns[2]] == null
        rows[0][columns[3]] <=> 169 == 0
        rows[0][columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2004-08-27 10:45:32')
        rows[1][columns[0]] == '123456'
        rows[1][columns[1]] == 'Description about subject 123456'
        rows[1][columns[2]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2016-03-21 10:36:01')
        rows[1][columns[3]] == -1
        rows[1][columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2005-05-24 13:40:00')

        cleanup:
        if(transformedView) transformedView.close()
    }

}
