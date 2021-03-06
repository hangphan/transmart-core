package org.transmartproject.core.ontology

import org.transmartproject.core.exceptions.NoSuchResourceException
import org.transmartproject.core.users.User

interface MDStudiesResource {

    /**
     * Returns all studies the user has access to.
     *
     * @return the studies.
     */
    List<MDStudy> getStudies(User user)

    /**
     * Returns the study with the given id.
     *
     * @param id the database identifier of the study
     * @return the study with the id, if it exists and the user has access to the study.
     * @throws NoSuchResourceException iff no study can be found with the id or the user does not have
     * access to the study.
     */
    MDStudy getStudyForUser(Long id, User user) throws NoSuchResourceException

    /**
     * Returns the study with the given study name.
     *
     * @param id the unique study name of the study
     * @return the study with the name, if it exists and the user has access to the study.
     * @throws NoSuchResourceException iff no study can be found with the name or the user does not have
     * access to the study.
     */
    MDStudy getStudyByStudyIdForUser(String studyId, User user) throws NoSuchResourceException

}
