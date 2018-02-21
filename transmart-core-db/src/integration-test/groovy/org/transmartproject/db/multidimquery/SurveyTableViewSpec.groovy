package org.transmartproject.db.multidimquery

import com.google.common.collect.ImmutableList
import grails.test.mixin.integration.Integration
import grails.transaction.Rollback
import org.springframework.beans.factory.annotation.Autowired
import org.transmartproject.core.concept.ConceptsResource
import org.transmartproject.core.dataquery.MetadataAwareDataColumn
import org.transmartproject.core.multidimquery.Hypercube
import org.transmartproject.core.multidimquery.MultiDimensionalDataResource
import org.transmartproject.core.ontology.MDStudiesResource
import org.transmartproject.db.clinical.SurveyTableColumnService
import org.transmartproject.core.multidimquery.query.Constraint
import org.transmartproject.core.multidimquery.query.StudyNameConstraint
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

    @Autowired
    MDStudiesResource studiesResource

    @Autowired
    ConceptsResource conceptsResource

    @Autowired
    SurveyTableColumnService surveyTableColumnService

    private final UTC = TimeZone.getTimeZone('UTC')

    def 'survey 1'() {
        setup:
        def user = User.findByUsername('test-public-user-1')
        Constraint constraint = new StudyNameConstraint(studyId: "SURVEY1")
        Hypercube hypercube = multiDimService.retrieveClinicalData(constraint, user, [DimensionImpl.PATIENT])
        boolean includeMeasurementDateColumns = true

        when:
        List<HypercubeDataColumn> hypercubeColumns = surveyTableColumnService.getHypercubeDataColumnsForConstraint(constraint, user)
        List<MetadataAwareDataColumn> columnList = ImmutableList.copyOf(surveyTableColumnService.getMetadataAwareColumns(
                hypercubeColumns, includeMeasurementDateColumns))
        def transformedView = new SurveyTableView(columnList, hypercube)
        then: 'header matches expectations'
        def columns = transformedView.indicesList
        columns*.label == ['FISNumber', 'birthdate1', 'birthdate1.date', 'favouritebook', 'favouritebook.date',
                           'gender1', 'gender1.date', 'nmultbab', 'nmultbab.date', 'nmultfam', 'nmultfam.date',
                           'sport', 'sport.date', 'twin', 'twin.date']
        then: 'columns metadata matches expectations'
        def metadata = columns*.metadata
        metadata*.type == [NUMERIC, DATE, DATE, STRING, DATE, NUMERIC, DATE, STRING, DATE, STRING, DATE, STRING, DATE, STRING, DATE]
        metadata*.measure == [SCALE, SCALE, SCALE, NOMINAL, SCALE, NOMINAL, SCALE, NOMINAL, SCALE, NOMINAL, SCALE, NOMINAL, SCALE, NOMINAL, SCALE]
        metadata*.description == ['FIS Number', 'Birth Date', 'Date of measurement', 'Favourite Book',
                                  'Date of measurement', 'Gender', 'Date of measurement', 'Number of children that are multiplet',
                                  'Date of measurement', 'Numbers of multiples in family', 'Date of measurement',
                                  'How often do you do sport activities?', 'Date of measurement', 'Is a Twin', 'Date of measurement']
        metadata*.width == [12, 22, 22, 400, 22, 12, 22, 25, 22, 25, 22, null, 22, 25, 22]
        metadata*.decimals == [0, null, null, null, null, null, null, null, null, null, null, null, null, null, null]
        metadata*.columns == [12, 22, 22, 400, 22, 14, 22, 25, 22, 25, 22, null, 22, 25, 22]
        def height1Metadata = columns.find { it.label == 'gender1' }.metadata
        height1Metadata.valueLabels == [(new BigDecimal(1)): 'Female', (new BigDecimal(2)): 'Male', (new BigDecimal(-2)): 'Not Specified']
        height1Metadata.missingValues.values == [new BigDecimal(-2)]

        when: 'get row'
        def rows = transformedView.rows.toList()
        then: 'content matches expectations'
        rows.size() == 14
        def secondSubjRow = rows.find { row ->  row[columns[0]] == '2' }
        secondSubjRow
        secondSubjRow[columns[0]] == '2'
        secondSubjRow[columns[1]] == Date.parse('yyyy-MM-dd hh:mm:ss', '1986-10-22 00:00:00', UTC)
        secondSubjRow[columns[2]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2010-12-16 20:23:15')
        that secondSubjRow[columns[3]] as String, containsString('Dostoyevsky')
        secondSubjRow[columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2016-03-21 10:36:01')
        secondSubjRow[columns[5]] == -2
        secondSubjRow[columns[6]] == null
        secondSubjRow[columns[11]] == '3'
        def firstSubjRow = rows.find { row ->  row[columns[0]] == '1' }
        firstSubjRow[columns[0]] == '1'
        firstSubjRow[columns[1]] == Date.parse('yyyy-MM-dd hh:mm:ss', '1980-08-12 00:00:00', UTC)
        firstSubjRow[columns[2]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2015-11-14 19:05:00')
        that firstSubjRow[columns[3]] as String, containsString('Hofstadter')
        firstSubjRow[columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2016-03-21 10:36:01')
        firstSubjRow[columns[5]] == 2
        firstSubjRow[columns[6]] == null
        firstSubjRow[columns[11]] == 10

        when: 'do not include MeasurementDateColumn'
        includeMeasurementDateColumns = false
        if(transformedView) transformedView.close()
        columnList = ImmutableList.copyOf(surveyTableColumnService.getMetadataAwareColumns(
                hypercubeColumns, includeMeasurementDateColumns))
        transformedView = new SurveyTableView(columnList, hypercube)
        then: 'header matches expectations'
        def columns2 = transformedView.indicesList
        columns2*.label == ['FISNumber', 'birthdate1', 'favouritebook', 'gender1', 'nmultbab', 'nmultfam', 'sport', 'twin']
        then: 'columns metadata matches expectations'
        def metadata2 = columns2*.metadata
        metadata2*.type == [NUMERIC, DATE, STRING, NUMERIC, STRING, STRING, STRING, STRING]
        metadata2*.measure == [SCALE, SCALE, NOMINAL, NOMINAL, NOMINAL, NOMINAL, NOMINAL, NOMINAL]
        metadata2*.description == ['FIS Number', 'Birth Date', 'Favourite Book', 'Gender', 'Number of children that are multiplet',
                                  'Numbers of multiples in family', 'How often do you do sport activities?', 'Is a Twin']

        cleanup:
        if(transformedView) transformedView.close()
    }

    def 'survey 2'() {
        setup:
        def user = User.findByUsername('test-public-user-1')
        Constraint constraint = new StudyNameConstraint(studyId: "SURVEY2")
        Hypercube hypercube = multiDimService.retrieveClinicalData(constraint, user, [DimensionImpl.PATIENT])
        boolean includeMeasurementDateColumns = true

        when:
        List<HypercubeDataColumn> hypercubeColumns = surveyTableColumnService.getHypercubeDataColumnsForConstraint(constraint, user)
        List<MetadataAwareDataColumn> columnList = ImmutableList.copyOf(
                surveyTableColumnService.getMetadataAwareColumns(hypercubeColumns, includeMeasurementDateColumns))
        def transformedView = new SurveyTableView(columnList, hypercube)
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
        then: 'content matches expectations'
        rows[0][columns[0]] == '2'
        rows[0][columns[1]] == 'No description'
        rows[0][columns[2]] == null
        rows[0][columns[3]] <=> 169 == 0
        rows[0][columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2004-08-27 10:45:32')
        rows[1][columns[0]] == '1'
        rows[1][columns[1]] == 'Description about subject 1'
        rows[1][columns[2]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2016-03-21 10:36:01')
        rows[1][columns[3]] == -1
        rows[1][columns[4]] == Date.parse('yyyy-MM-dd hh:mm:ss', '2005-05-24 13:40:00')

        when: 'do not include MeasurementDateColumn'
        includeMeasurementDateColumns = false
        if(transformedView) transformedView.close()
        columnList = ImmutableList.copyOf(
                surveyTableColumnService.getMetadataAwareColumns(hypercubeColumns, includeMeasurementDateColumns))
        transformedView = new SurveyTableView(columnList, hypercube)
        then: 'header matches expectations'
        def columns2 = transformedView.indicesList
        columns2*.label == ['FISNumber', 'description', 'height1']
        then: 'columns metadata matches expectations'
        def metadata2 = columns2*.metadata
        metadata2*.type == [NUMERIC, STRING, NUMERIC]
        metadata2*.measure == [SCALE, NOMINAL, SCALE]
        metadata2*.description == ['FIS Number', 'Description', 'Height']

        cleanup:
        if(transformedView) transformedView.close()
    }

}
