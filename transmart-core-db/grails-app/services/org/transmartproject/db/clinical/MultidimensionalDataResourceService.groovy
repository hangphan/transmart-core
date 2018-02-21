/* (c) Copyright 2017, tranSMART Foundation, Inc. */

package org.transmartproject.db.clinical

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import grails.orm.HibernateCriteriaBuilder
import grails.transaction.Transactional
import grails.util.Holders
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.TupleConstructor
import org.apache.commons.lang.NotImplementedException
import org.hibernate.Criteria
import org.hibernate.criterion.*
import org.hibernate.internal.CriteriaImpl
import org.hibernate.internal.StatelessSessionImpl
import org.hibernate.type.StandardBasicTypes
import org.springframework.beans.factory.annotation.Autowired
import org.transmartproject.core.IterableResult
import org.transmartproject.core.dataquery.TabularResult
import org.transmartproject.core.dataquery.assay.Assay
import org.transmartproject.core.dataquery.highdim.HighDimensionDataTypeResource
import org.transmartproject.core.dataquery.highdim.assayconstraints.AssayConstraint
import org.transmartproject.core.dataquery.highdim.dataconstraints.DataConstraint
import org.transmartproject.core.dataquery.highdim.projections.Projection
import org.transmartproject.core.exceptions.EmptySetException
import org.transmartproject.core.exceptions.InvalidArgumentsException
import org.transmartproject.core.exceptions.NoSuchResourceException
import org.transmartproject.core.multidimquery.query.BiomarkerConstraint
import org.transmartproject.core.multidimquery.query.Combination
import org.transmartproject.core.multidimquery.query.ConceptConstraint
import org.transmartproject.core.multidimquery.query.Constraint
import org.transmartproject.core.exceptions.UnexpectedResultException
import org.transmartproject.core.multidimquery.Dimension
import org.transmartproject.core.multidimquery.Hypercube
import org.transmartproject.core.multidimquery.query.Constraint
import org.transmartproject.core.multidimquery.MultiDimensionalDataResource
import org.transmartproject.core.multidimquery.query.QueryBuilder
import org.transmartproject.core.multidimquery.query.StudyNameConstraint
import org.transmartproject.core.multidimquery.query.StudyObjectConstraint
import org.transmartproject.core.ontology.MDStudiesResource
import org.transmartproject.core.querytool.QueryResult
import org.transmartproject.core.querytool.QueryResultType
import org.transmartproject.core.querytool.QueryStatus
import org.transmartproject.core.users.User
import org.transmartproject.db.dataquery.highdim.HighDimensionDataTypeResourceImpl
import org.transmartproject.db.dataquery.highdim.HighDimensionResourceService
import org.transmartproject.db.i2b2data.ObservationFact
import org.transmartproject.db.i2b2data.Study
import org.transmartproject.db.metadata.DimensionDescription
import org.transmartproject.db.multidimquery.*
import org.transmartproject.db.multidimquery.query.*
import org.transmartproject.db.querytool.*
import org.transmartproject.db.support.ParallelPatientSetTaskService
import org.transmartproject.db.user.User as DbUser
import org.transmartproject.db.util.HibernateUtils

import static org.transmartproject.db.multidimquery.DimensionImpl.*

class MultidimensionalDataResourceService extends AbstractDataResourceService implements MultiDimensionalDataResource {

    @Autowired
    HighDimensionResourceService highDimensionResourceService

    @Autowired
    MDStudiesResource studiesResource

    @Autowired
    ParallelPatientSetTaskService parallelPatientSetTaskService

    @Override Dimension getDimension(String name) {
        def dimension = getBuiltinDimension(name)
        if (!dimension) {
            dimension = fromName(name)
        }
        dimension
    }

    @Transactional(readOnly = true)
    @Memoized
    QtQueryResultType getPatientSetResultType() {
        QtQueryResultType.findById(QueryResultType.PATIENT_SET_ID)
    }

    /**
     * @param user: The current user.
     * @param dataType: The string identifying the data type. "clinical" for clinical data, for high dimensional data
     * the appropriate identifier string (hdd is not yet implemented).
     * @param constraints: (nullable) A list of Constraint-s. If null, selects all the data in the database.
     * @param dimensions: (nullable) A list of Dimension-s to select. Only dimensions valid for the selected studies
     * will actually be applied. If null, select all available dimensions.
     *
     * Not yet implemented:
     * @param sort
     *
     * @return a Hypercube result
     */
    @Override HypercubeImpl retrieveData(Map args, String dataType, User user) {
        // Supporting a native Hypercube implementation for high dimensional data is the intention here. As of yet
        // that has not been implemented, so we only support clinical data in this call. Instead there is the
        // highDimension call that uses the old high dim api and converts the tabular result to a hypercube.
        if(dataType != "clinical") throw new NotImplementedException("High dimension datatypes are not yet implemented")

        Constraint constraint = args.constraint
        Set<DimensionImpl> dimensions = ImmutableSet.copyOf(args.dimensions.collect { toDimensionImpl(it) } ?: [])

        Set<? extends Dimension> supportedDimensions = getSupportedDimensions(constraint)
        // only allow valid dimensions
        dimensions = (Set<DimensionImpl>) dimensions?.findAll { it in supportedDimensions } ?: supportedDimensions

        List<DimensionImpl> orderByDimensions = ImmutableList.copyOf(args.sort.collect { toDimensionImpl(it) } ?: [])
        assert (orderByDimensions - dimensions).empty : 'Some dimensions were not found in this result set to sort by'

        CriteriaImpl hibernateCriteria = buildCriteria(dimensions, orderByDimensions)
        HibernateCriteriaQueryBuilder restrictionsBuilder = getCheckedQueryBuilder(user)
        // TODO: check that aliases set by dimensions and by restrictions don't clash

        restrictionsBuilder.applyToCriteria(hibernateCriteria, [constraint])

        // session will be closed by the Hypercube
        new HypercubeImpl(dimensions, hibernateCriteria)
    }

    @Memoized
    @Transactional(readOnly = true)
    List<Dimension> getAllDimensions() {
        DimensionDescription.allDimensions
    }

    Set<? extends Dimension> getSupportedDimensions(Constraint constraint) {
        // Add any studies that are being selected on
        def studyIds = findStudyNameConstraints(constraint)*.studyId
        Set studies = (studyIds.empty ? [] : studyIds.collect { studiesResource.getStudyByStudyId(it) }) +
                findStudyObjectConstraints(constraint)*.study as Set

        //TODO Remove after adding all the dimension, added to prevent e2e tests failing
        def notImplementedDimensions = [AssayDimension, BioMarkerDimension, ProjectionDimension]
        // This throws a LegacyStudyException for non-17.1 style studies
        // This could probably be done more efficiently, but GORM support for many-to-many collections is pretty
        // buggy. And usually the studies and dimensions will be cached in memory.
        List<Dimension> availableDimensions = studies ? studies*.dimensions.flatten()
                : allDimensions
        ImmutableSet.copyOf availableDimensions.findAll {
            !(it.class in notImplementedDimensions)
        }
    }

    private CriteriaImpl buildCriteria(Set<DimensionImpl> dimensions, List<DimensionImpl> orderByDimensions) {
        def nonSortableDimensions = orderByDimensions.findAll { !(it instanceof AliasAwareDimension) }
        assert !nonSortableDimensions : 'Sorting over following dimensions is not supported: ' +  nonSortableDimensions

        // We need methods from different interfaces that StatelessSessionImpl implements.
        def session = (StatelessSessionImpl) sessionFactory.openStatelessSession()
        session.connection().autoCommit = false

        HibernateCriteriaBuilder q = HibernateUtils.createCriteriaBuilder(ObservationFact, 'observation_fact', session)
        q.with {
            // The main reason to use this projections block is that it clears all the default projections that
            // select all fields.
            projections {
                // NUM_FIXED_PROJECTIONS must match the number of projections defined here
                property 'valueType', 'valueType'
                property 'textValue', 'textValue'
                property 'numberValue', 'numberValue'
                property 'rawValue', 'rawValue'
            }
        }

        Query query = new Query(q, [modifierCodes: ['@']])

        dimensions.each {
            it.selectIDs(query)
        }
        boolean hasModifiers = dimensions.any { it.implementationType == ImplementationType.MODIFIER }
        if (hasModifiers) {
            // Make sure all primary key dimension columns are selected, even if they are not part of the result
            primaryKeyDimensions.each {
                if (!(it in dimensions)) {
                    it.selectIDs(query)
                }
            }

            q.with {
                // instanceNum is not a dimension
                property 'instanceNum', 'instanceNum'

                // Modifier dimension does not implement AliasAwareDimension interface. So it's excluded from the list.
                (orderByDimensions + primaryKeyDimensions).unique().each { AliasAwareDimension aaDim ->
                    order aaDim.alias
                }
                order 'trialVisit'
                order 'instanceNum'
            }
        } else {
            q.with {
                orderByDimensions.each { AliasAwareDimension aaDim ->
                    order aaDim.alias
                }
            }
        }

        q.with {
            inList 'modifierCd', query.params.modifierCodes
        }

        (CriteriaImpl)query.criteria.instance
    }

    static final List<AliasAwareDimension> primaryKeyDimensions = ImmutableList.of(
            // primary key columns excluding modifierCd and instanceNum
            CONCEPT, PROVIDER, PATIENT, VISIT, START_TIME)

    private static DimensionImpl toDimensionImpl(dimOrDimName) {
        if(dimOrDimName instanceof DimensionImpl) {
            return dimOrDimName
        }
        if(dimOrDimName instanceof String) {
            def dim = DimensionDescription.findByName(dimOrDimName)?.dimension
            if(dim == null) throw new InvalidArgumentsException("Unknown dimension: $dimOrDimName")
            return dim
        }
        throw new InvalidArgumentsException("dimension $dimOrDimName is not a valid dimension or dimension name")
    }

    /*
    note: efficiently extracting the available dimension elements for dimensions is possible using nonstandard
    sql. For Postgres:
    SELECT array_agg(DISTINCT patient_num), array_agg(DISTINCT concept_cd),
        array_agg(distinct case when modifier_cd = 'SOMEMODIFIER' then tval_char end)... FROM observation_facts WHERE ...
      Maybe doing something with unnest() can also help but I haven't figured that out yet.
    For Oracle we should look into the UNPIVOT operator
     */


    @Lazy
    private Criterion defaultHDModifierCriterion = Restrictions.in('modifierCd',
            highDimensionResourceService.knownMarkerTypes.collect { "TRANSMART:HIGHDIM:${it.toUpperCase()}".toString() })

    private Criterion HDModifierCriterionForType(String type) {
        HighDimensionDataTypeResourceImpl hdres = highDimensionResourceService.getSubResourceForType(type)
        Restrictions.in('modifierCd',
                hdres.platformMarkerTypes.collect {"TRANSMART:HIGHDIM:${it.toUpperCase()}".toString()} )
    }

    /**
     * @description Function for getting a list of elements of a specified dimension
     * that are meeting a specified criteria and the user has access to.
     *
     * @param dimensionName
     * @param user
     * @param constraint
     */
    @Override
    IterableResult getDimensionElements(Dimension dimension, Constraint constraint, User user) {
        if(constraint) checkAccess(constraint, user)
        HibernateCriteriaQueryBuilder builder = getCheckedQueryBuilder(user)
        DetachedCriteria dimensionCriteria = builder.buildElementsCriteria((DimensionImpl) dimension, constraint)

        return getIterable(dimensionCriteria)
    }

    /**
     * Synchronously executes the query and tracks its progress by saving the status.
     * @param name meaningful text for the user to find back the query results.
     * @param user user that executes the query
     * @param constraintText textual representation of the constraint
     * @param apiVersion v1 or v2
     * @param queryResultType the type of the result set.
     * Patient set and generic query result (no assoc set) are supported at the moment.
     * @param queryExecutor closure that actually executes the query.
     * It gets the query result object and returns the number of the result set.
     * It also responsible for saving all actual results.
     * @return the query result object that does not contains result as such but rather status of execution
     * and result instance id you might use to fetch actual results.
     */
    QtQueryResultInstance createQueryResult(String name,
                                            User user,
                                            Constraint constraint,
                                            String apiVersion,
                                            QtQueryResultType queryResultType,
                                            Closure<Long> queryExecutor) {
        // 1. Populate or reuse qt_query_master
        Object queryMaster = createOrReuseQueryMaster(user, constraint, name, apiVersion)
        // 2. Populate qt_query_instance
        def queryInstance = new QtQueryInstance()
        queryInstance.userId = user.username
        queryInstance.groupId = Holders.grailsApplication.config.org.transmartproject.i2b2.group_id
        queryInstance.startDate = new Date()
        queryInstance.statusTypeId = QueryStatus.PROCESSING.id
        queryInstance.queryMaster = queryMaster
        queryMaster.addToQueryInstances(queryInstance)
        queryInstance.save(failOnError: true)
        // 3. Populate qt_query_result_instance
        def resultInstance = new QtQueryResultInstance()
        resultInstance.statusTypeId = QueryStatus.PROCESSING.id
        resultInstance.startDate = new Date()
        resultInstance.queryInstance = queryInstance
        resultInstance.queryResultType = queryResultType
        resultInstance.description = name
        resultInstance.save(failOnError: true, flush: true)
        try {
            // 4. Execute the query
            resultInstance.setSize = queryExecutor(resultInstance)

            // 5a. Update the result
            resultInstance.endDate = new Date()
            resultInstance.statusTypeId = QueryStatus.FINISHED.id
            queryInstance.endDate = new Date()
            queryInstance.statusTypeId = QueryStatus.FINISHED.id
        } catch (Throwable t) {
            // 5b. Update the result with the error message
            resultInstance.setSize = resultInstance.realSetSize = -1
            resultInstance.errorMessage = t.message
            resultInstance.endDate = new Date()
            resultInstance.statusTypeId = QueryStatus.ERROR.id
            queryInstance.endDate = new Date()
            queryInstance.statusTypeId = QueryStatus.ERROR.id
        }
        resultInstance
    }

    private QtQueryMaster createOrReuseQueryMaster(User user, Constraint constraint, name, apiVersion) {
        def constraintText = constraint.toJson()
        def queryMasterCriteria = DetachedCriteria.forClass(QtQueryMaster, 'qm')
                .add(Restrictions.eq('qm.userId', user.username))
                .add(Restrictions.eq('qm.deleteFlag', 'N'))
                .add(Restrictions.eq('qm.requestConstraints', constraintText))
                .addOrder(Order.desc('qm.createDate'))

        QtQueryMaster queryMaster = getFirst(queryMasterCriteria)
        if (queryMaster == null) {
            queryMaster = new QtQueryMaster()
            queryMaster.name = name
            queryMaster.userId = user.username
            queryMaster.groupId = Holders.grailsApplication.config.org.transmartproject.i2b2.group_id
            queryMaster.createDate = new Date()
            queryMaster.requestConstraints = constraintText
            queryMaster.apiVersion = apiVersion
            queryMaster.save(failOnError: true)
        }
        return queryMaster
    }

    /**
     * Find a query result based on a constraint.
     * @param user the creator of the query result.
     * @param constraint the constraint used in the lookup.
     * @param queryResultType
     * @return the query result if it exists; null otherwise.
     */
    QueryResult findQueryResultByConstraint(User user,
                                            Constraint constraint,
                                            QtQueryResultType queryResultType) {
        def criteria = DetachedCriteria.forClass(QtQueryResultInstance.class, 'qri')
                .createCriteria('qri.queryInstance', 'qi')
                .createCriteria('qi.queryMaster', 'qm')
                .add(Restrictions.eq('qri.queryResultType', queryResultType))
                .add(Restrictions.eq('qri.deleteFlag', 'N'))
                .add(Restrictions.eq('qri.statusTypeId', (short)QueryStatus.FINISHED.id))
                .add(Restrictions.eq('qi.userId', user.username))
                .add(Restrictions.eq('qi.deleteFlag', 'N'))
                .add(Restrictions.eq('qm.requestConstraints', constraint.toJson()))
                .add(Restrictions.eq('qm.deleteFlag', 'N'))
                .addOrder(Order.desc('qri.endDate'))
        (QueryResult)getFirst(criteria)
    }

    /**
     * Tries to reuse query result that satisfy provided constraint for the user before creating it.
     * @return A new one or reused query result.
     */
    QueryResult createOrReuseQueryResult(String name,
                                             User user,
                                             Constraint constraint,
                                             String apiVersion,
                                             QtQueryResultType queryResultType,
                                             Closure<Long> queryExecutor) {

        QueryResult result = findQueryResultByConstraint(user, constraint, queryResultType) ?:
                createQueryResult(name, user, constraint, apiVersion, queryResultType, queryExecutor)
        if (result.status != QueryStatus.FINISHED) {
            throw new UnexpectedResultException('Query not finished.')
        }
        result
    }

    /**
     * @description Function for creating a patient set consisting of patients for which there are observations
     * that are specified by <code>query</code>.
     *
     * FIXME: this implementation was copied from QueriesResourceService.runQuery and modified. The two copies should
     * be folded together.
     *
     * @param query
     * @param user
     */
    @Override
    @Transactional
    QueryResult createPatientSetQueryResult(String name,
                                            Constraint constraint,
                                            User user,
                                            String apiVersion) {
        checkAccess(constraint, user)

        createQueryResult(
                name,
                user,
                constraint,
                apiVersion,
                patientSetResultType,
                { QtQueryResultInstance queryResult -> populatePatientSetQueryResult(queryResult, constraint, user) }
        )
    }

    /**
     * The same as {@link this.createPatientSetQueryResult}, but first ties to reuse existing patient set that satisfies
     * provided constraints
     * @return A new ore reused patient set.
     */
    @Override
    @Transactional
    QueryResult createOrReusePatientSetQueryResult(String name,
                                            Constraint constraint,
                                            User user,
                                            String apiVersion) {
        checkAccess(constraint, user)

        createOrReuseQueryResult(
                name,
                user,
                constraint,
                apiVersion,
                patientSetResultType,
                { QtQueryResultInstance queryResult -> populatePatientSetQueryResult(queryResult, constraint, user) }
        )
    }

    /**
     * Populates given query result with patient set that satisfy provided constraints with regards with user access rights.
     * @param queryResult query result to populate with patients
     * @param constraint constraint to get results that satisfy it
     * @param user user for whom to execute the patient set query. Result will depend on the user access rights.
     * @return Number of patients inserted in the patient set
     */
    @CompileStatic
    private Integer populatePatientSetQueryResult(QtQueryResultInstance queryResult, Constraint constraint, User user) {
        assert queryResult
        assert queryResult.id

        DetachedCriteria patientSetDetachedCriteria = getCheckedQueryBuilder(user).buildCriteria(constraint)
                .setProjection(
                Projections.projectionList()
                        .add(Projections.distinct(Projections.property('patient.id')), 'pid')
                        .add(Projections.sqlProjection("${queryResult.id} as rid", ['rid'] as String[],
                        [StandardBasicTypes.LONG] as org.hibernate.type.Type[])))

        Criteria patientSetCriteria = getExecutableCriteria(patientSetDetachedCriteria)
        return HibernateUtils
                .insertResultToTable(QtPatientSetCollection, ['patient.id', 'resultInstance.id'], patientSetCriteria)
    }

    @Override
    QueryResult findQueryResult(Long queryResultId, User user) {
        def criteria = DetachedCriteria.forClass(QtQueryResultInstance.class, 'qri')
                .createCriteria('qri.queryInstance', 'qi')
                .createCriteria('qi.queryMaster', 'qm')
                .add(Restrictions.eq('qri.queryResultType.id', QueryResultType.PATIENT_SET_ID))
                .add(Restrictions.eq('qri.deleteFlag', 'N'))
                .add(Restrictions.eq('qri.statusTypeId', (short)QueryStatus.FINISHED.id))
                .add(Restrictions.eq('qi.userId', user.username))
                .add(Restrictions.eq('qi.deleteFlag', 'N'))
                .add(Restrictions.eq('qm.deleteFlag', 'N'))
                .add(Restrictions.eq('qri.id', queryResultId))
        def queryResult = getFirst(criteria) as QtQueryResultInstance
        if (queryResult == null) {
            throw new NoSuchResourceException("Patient set not found with id ${queryResultId} for user ${user.username}.")
        }
        queryResult
    }

    @Override
    Iterable<QueryResult> findPatientSetQueryResults(User user) {
        def criteria = DetachedCriteria.forClass(QtQueryResultInstance.class, 'qri')
                .createCriteria('qri.queryInstance', 'qi')
                .createCriteria('qi.queryMaster', 'qm')
                .add(Restrictions.eq('qri.queryResultType.id', QueryResultType.PATIENT_SET_ID))
                .add(Restrictions.eq('qri.deleteFlag', 'N'))
                .add(Restrictions.eq('qri.statusTypeId', (short)QueryStatus.FINISHED.id))
                .add(Restrictions.eq('qi.userId', user.username))
                .add(Restrictions.eq('qi.deleteFlag', 'N'))
                .add(Restrictions.eq('qm.deleteFlag', 'N'))
        return getIterable(criteria)
    }

    static List<StudyNameConstraint> findStudyNameConstraints(Constraint constraint) {
        if (constraint instanceof StudyNameConstraint) {
            return [constraint]
        } else if (constraint instanceof Combination) {
            constraint.args.collectMany { findStudyNameConstraints(it) }
        } else {
            return []
        }
    }

    static List<StudyObjectConstraint> findStudyObjectConstraints(Constraint constraint) {
        if (constraint instanceof StudyObjectConstraint) {
            return [constraint]
        } else if (constraint instanceof Combination) {
            constraint.args.collectMany { findStudyObjectConstraints(it) }
        } else {
            return []
        }
    }

    static List<ConceptConstraint> findConceptConstraints(Constraint constraint) {
        if (constraint instanceof ConceptConstraint) {
            return [constraint]
        } else if (constraint instanceof Combination) {
            constraint.args.collectMany { findConceptConstraints(it) }
        } else {
            return []
        }
    }

    private static List<BiomarkerConstraint> findAllBiomarkerConstraints(Constraint constraint) {
        if (constraint instanceof BiomarkerConstraint) {
            return [constraint]
        } else if (constraint instanceof Combination) {
            constraint.args.collectMany { findAllBiomarkerConstraints(it) }
        } else {
            return []
        }
    }

    @CompileStatic
    @Override
    Hypercube highDimension(
            Constraint assayConstraint,
            BiomarkerConstraint biomarkerConstraint = new BiomarkerConstraint(),
            String projectionName = Projection.ALL_DATA_PROJECTION,
            User user,
            String type) {
        projectionName = projectionName ?: Projection.ALL_DATA_PROJECTION
        checkAccess(assayConstraint, user)

        List<AssayConstraint> oldAssayConstraints = getOldAssayConstraint(assayConstraint, user, type)
        if(!oldAssayConstraints) {
            return new EmptyHypercube()
        }

        HighDimensionDataTypeResource typeResource
        if(type == 'autodetect') {
            Map<HighDimensionDataTypeResource, Collection<Assay>> assaysByType =
                    highDimensionResourceService.getSubResourcesAssayMultiMap(oldAssayConstraints)
            if (assaysByType.size() == 1) {
                typeResource = assaysByType.keySet()[0]
            } else {
                assert assaysByType.size() != 0, "cannot happen"
                throw new InvalidQueryException("Autodetecting the high dimensional type found multiple applicable " +
                        "types: ${assaysByType.keySet()*.dataTypeName.join(', ')}. Please choose one.")
            }
        } else {
            try {
                typeResource = highDimensionResourceService.getSubResourceForType(type)
            } catch (NoSuchResourceException e) {
                throw new InvalidQueryException("Unknown high dimensional data type.", e)
            }
        }

        Projection projection = typeResource.createProjection(projectionName)

        List<DataConstraint> dataConstraints = []
        if (biomarkerConstraint?.biomarkerType) {
            dataConstraints << typeResource.createDataConstraint(biomarkerConstraint.params, biomarkerConstraint.biomarkerType)
        }
        try {
            TabularResult table = typeResource.retrieveData(oldAssayConstraints, dataConstraints, projection)
            return new HddTabularResultHypercubeAdapter(table)
        } catch (EmptySetException e) {
            return new EmptyHypercube()
        }
    }

    @Override
    List retrieveHighDimDataTypes(Constraint assayConstraint, User user){
        assert assayConstraint instanceof Constraint
        checkAccess(assayConstraint, user)
        List<AssayConstraint> oldAssayConstraints = getOldAssayConstraint(assayConstraint, user, 'autodetect')
        if(!oldAssayConstraints) {
            return []
        }

        Map<HighDimensionDataTypeResource, Collection<Assay>> assaysByType =
                highDimensionResourceService.getSubResourcesAssayMultiMap(oldAssayConstraints)

        assaysByType.keySet()?.collect {it.dataTypeName}
    }

    private List<Study> selectStudiesWithDimensionSupport(Iterable<Study> studies, DimensionImpl dimension) {
        DetachedCriteria studiesCriteria = DetachedCriteria.forClass(Study)
            .createAlias('dimensionDescriptions', 'd')
            .add(Restrictions.in('id', studies*.id))
            .add(Restrictions.in('d.name', dimension.name))

        getList(studiesCriteria)
    }

    private List<AssayConstraint> getOldAssayConstraint(Constraint assayConstraint, User user, String type) {
        assert user instanceof DbUser

        Collection<Study> userStudies = accessControlChecks.getDimensionStudiesForUser(user)
        List<Study> assaySupportStudies = selectStudiesWithDimensionSupport(userStudies, ASSAY)
        if (!assaySupportStudies) {
            log.debug("No studies with assay dimension for user ${user.name} were found.")
            return Collections.emptyList()
        }

        QueryBuilder builder = HibernateCriteriaQueryBuilder.forStudies(assaySupportStudies)

        Criterion modifierCriterion = type == 'autodetect' ?
                defaultHDModifierCriterion : HDModifierCriterionForType(type)

        DetachedCriteria assayIdsCriteria = builder.buildCriteria(assayConstraint, modifierCriterion)
                .setProjection(Projections.property('numberValue'))

        List<BigDecimal> assayIds = getList(assayIdsCriteria)

        if (assayIds.empty) {
            Collections.emptyList()
        } else {
            [
                    highDimensionResourceService
                            .createAssayConstraint([ids: assayIds] as Map, AssayConstraint.ASSAY_ID_LIST_CONSTRAINT)
            ]
        }
    }

    @Override
    Hypercube retrieveClinicalData(Constraint constraint, User user, List<Dimension> orderByDimensions = []) {
        assert constraint instanceof Constraint
        checkAccess(constraint, user)
        def dataType = 'clinical'
        retrieveData(dataType, user, constraint: constraint, sort: orderByDimensions)
    }

}

@TupleConstructor
class Query {
    HibernateCriteriaBuilder criteria
    Map params
}

