/*
 * Copyright / Copr. 2010-2013 Atos - Public Sector France -
 * BS & Innovation for the DataLift project,
 * Contributor(s) : L. Bihanic, H. Devos, O. Ventura, M. Chetima
 *
 * Contact: dlfr-datalift@atos.net
 *
 * This software is governed by the CeCILL license under French law and
 * abiding by the rules of distribution of free software. You can use,
 * modify and/or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty and the software's author, the holder of the
 * economic rights, and the successive licensors have only limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading, using, modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean that it is complicated to manipulate, and that also
 * therefore means that it is reserved for developers and experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and, more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */

package org.datalift.core.project;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;

import com.clarkparsia.empire.annotation.RdfId;
import com.clarkparsia.empire.annotation.RdfProperty;
import com.clarkparsia.empire.annotation.RdfsClass;

import org.datalift.fwk.project.Ontology;
import org.datalift.fwk.project.Project;
import org.datalift.fwk.project.Source;

import static org.datalift.fwk.rdf.RdfNamespace.VDPP;


/**
 * An implementation of {@link Project} that relies on Empire RDF JPA
 * provider for persistence.
 * <p>
 * Note: Empire namespace handling sucks as it uses namespace prefixes
 * as key in a global namespace table (RdfNamespace) rather than using
 * the namespace URI as key and consider prefixes as a local matter
 * (local to each query and class).
 * </p>
 * <p>
 * So, <strong>be warned</strong>, this classes relies on the following
 * <i>global</i> prefix mappings be installed:
 * </p>
 * <dl>
 * <dt>datalift</dt>
 * <dd>http://www.datalift.org/core#</dd>
 * <dt>dc</dt>
 * <dd>http://purl.org/dc/elements/1.1/</dd>
 * </dl>
 *
 * @author hdevos
 */
@Entity
@RdfsClass("vdpp:Project")
public class ProjectImpl extends BaseRdfEntity implements Project
{

    public enum Execution {
        Selection(VDPP.uri + "Selection"),
        Publication(VDPP.uri + "Publication"),
        Interlinking(VDPP.uri + "Interlinking"),
        Convertion(VDPP.uri + "Convertion");

        public final URI uri;

        Execution(String s) {
            try {
                this.uri = new URI(s);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    @RdfId
    private String uri;
    @RdfProperty("dc:title")
    private String title;
    @RdfProperty("dc:creator")
    private String owner;
    @RdfProperty("dc:description")
    private String description;

    @RdfProperty("datalift:source")
    @OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.ALL })
    private Collection<Source> sources = new LinkedList<Source>();

    @RdfProperty("dcterms:issued")
    private Date dateCreated;
    @RdfProperty("dcterms:modified")
    private Date dateModified;
    @RdfProperty("dcterms:license")
    private URI license;
    @RdfProperty("prv:Execution")
    private URI execution;

    @RdfProperty("void:vocabulary")
    @OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.ALL })
    private Collection<Ontology> ontologies = new LinkedList<Ontology>();

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public ProjectImpl() {
        // NOP
    }

    public ProjectImpl(String uri) {
        this.uri = uri;
    }

    //-------------------------------------------------------------------------
    // Project contract support
    //-------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public String getUri() {
        return this.uri;
    }

    /** {@inheritDoc} */
    @Override
    public String getTitle() {
        return title;
    }

    /** {@inheritDoc} */
    @Override
    public void setTitle(String t) {
        title = t;
    }

    /** {@inheritDoc} */
    @Override
    public String getOwner() {
        return owner;
    }

    /** {@inheritDoc} */
    @Override
    public void setOwner(String o) {
        owner = o;
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return description;
    }

    /** {@inheritDoc} */
    @Override
    public void setDescription(String d) {
        description = d;
    }

    /** {@inheritDoc} */
    @Override
    public void addSource(Source s) {
        sources.add(s);
    }

    /** {@inheritDoc} */
    @Override
    public Collection<Source> getSources() {
        return sources;
    }

    /** {@inheritDoc} */
    @Override
    public Source getSource(URI uri) {
        for (Source source : this.sources) {
            if (source.getUri().toString().equals(uri.toString())) {
                return source;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteSource(URI uri) {
        Source source = getSource(uri);
        if(source != null) {
            this.sources.remove(source);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Date getDateCreation() {
        return dateCreated;
    }

    /** {@inheritDoc} */
    @Override
    public void setDateCreation(Date date) {
        dateCreated = date;
    }

    /** {@inheritDoc} */
    @Override
    public Date getDateModification() {
        return dateModified;
    }

    /** {@inheritDoc} */
    @Override
    public void setDateModification(Date date) {
        this.dateModified = date;
    }

    /** {@inheritDoc} */
    @Override
    public URI getLicense() {
        return license;
    }

    /** {@inheritDoc} */
    @Override
    public void setLicense(URI license) {
        this.license = license;
    }

    /** {@inheritDoc} */
    @Override
    public void addOntology(Ontology src) {
        ontologies.add(src);
    }

    /** {@inheritDoc} */
    @Override
    public Collection<Ontology> getOntologies() {
        return ontologies;
    }

    /** {@inheritDoc} */
    @Override
    public Ontology getOntology(String title) {
        for (Ontology ontology : this.ontologies) {
            if (ontology.getTitle().equals(title)) {
                return ontology;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteOntology(String title) {
        Ontology ontology = getOntology(title);
        if(ontology != null) {
            this.ontologies.remove(ontology);
        }
    }

    /** {@inheritDoc} */
    @Override
    public URI getExecution() {
        return this.execution;
    }

    /** {@inheritDoc} */
    @Override
    public void setExecution(URI execution) {
        this.execution = execution;
    }

    //-------------------------------------------------------------------------
    // BaseRdfEntity contract support
    //-------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    protected void setId(String id) {
        this.uri = id;
    }
}