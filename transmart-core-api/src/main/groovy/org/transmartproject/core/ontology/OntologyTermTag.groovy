package org.transmartproject.core.ontology

/**
 * Key-value pair entry.
 * Tags hold additional statements about ontology term.
 */
interface OntologyTermTag {

    /**
     * @return Tag label name. It should be unique per ontology term.
     */
    String getName()

    /**
     * Path of the ontology term the tag is associated with.
     * @return
     */
    String getOntologyTermFullName()

    /**
     * @return Free text value.
     */
    String getDescription()

}
