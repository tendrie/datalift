package org.datalift.projectmanager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

import javax.persistence.Entity;
import javax.ws.rs.core.MediaType;

import org.openrdf.model.Statement;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.helpers.StatementCollector;
import org.openrdf.rio.ntriples.NTriplesParser;
import org.openrdf.rio.rdfxml.RDFXMLParser;
import org.openrdf.rio.trig.TriGParser;
import org.openrdf.rio.trix.TriXParser;
import org.openrdf.rio.turtle.TurtleParser;

import com.clarkparsia.empire.annotation.RdfsClass;

import org.datalift.fwk.MediaTypes;
import org.datalift.fwk.project.RdfSource;
import org.datalift.fwk.util.StringUtils;

import static org.datalift.fwk.MediaTypes.*;


@Entity
@RdfsClass("datalift:rdfSource")
public class RdfSourceImpl extends BaseFileSource<Statement>
                           implements RdfSource
{
    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    private transient Collection<Statement> content = null;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    public RdfSourceImpl() {
        super(SourceType.RdfSource);
    }

    public RdfSourceImpl(String uri) {
        super(SourceType.RdfSource, uri);
    }

    //-------------------------------------------------------------------------
    // FileSource contract support
    //-------------------------------------------------------------------------

    @Override
    public void init(File docRoot, URI baseUri) throws IOException {
        super.init(docRoot, baseUri);
        InputStream in = this.getInputStream();
        if (in != null) {
            RDFParser parser = null;
        	if ((this.getMimeType().equals(MediaTypes.TEXT_TURTLE)) ||
        			(this.getMimeType().equals(MediaTypes.TEXT_N3)))
        		parser = new TurtleParser();
        	else if (this.getMimeType().equals(MediaTypes.APPLICATION_NTRIPLES))
        		parser = new NTriplesParser();
        	else if (this.getMimeType().equals(MediaTypes.APPLICATION_TRIG))
        		parser = new TriGParser();
        	else if (this.getMimeType().equals(MediaTypes.APPLICATION_TRIX))
        		parser = new TriXParser();
        	else if (this.getMimeType().equals(MediaTypes.APPLICATION_RDF_XML))
        		parser = new RDFXMLParser();
            else {
                throw new IllegalArgumentException(
                                "Unsupported MIME type: " + this.getMimeType());
            }
            Collection<Statement> l = new LinkedList<Statement>();
            if (parser != null) {
                try {
                    StatementCollector collector = new StatementCollector(l);
                    parser.setRDFHandler(collector);
                    parser.parse(in, (baseUri != null)? baseUri.toString(): "");
                } catch (Exception e) {
                    throw new IOException("Error while parsing RDF source");
                }
            }
            this.content = Collections.unmodifiableCollection(l);
        }
    }

    @Override
    public void setMimeType(String mimeType) {
        super.setMimeType(parseMimeType(mimeType).toString());
    }

    //-------------------------------------------------------------------------
    // RdfSource contract support
    //-------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public Iterator<Statement> iterator() {
        if (this.content == null) {
            throw new IllegalStateException();
        }
        return this.content.iterator();
    }

    public static MediaType parseMimeType(String typeDesc) {
        MediaType mimeType = null;
        if (! StringUtils.isBlank(typeDesc)) {
            typeDesc = typeDesc.trim().toLowerCase();
            if ((TEXT_TURTLE.equals(typeDesc)) ||
                (APPLICATION_TURTLE.equals(typeDesc))) {
                mimeType = TEXT_TURTLE_TYPE;
            }
            else if ((TEXT_N3.equals(typeDesc)) ||
                     (TEXT_RDF_N3.equals(typeDesc)) ||
                     (APPLICATION_N3.equals(typeDesc))) {
                mimeType = TEXT_N3_TYPE;
            }
            else if ((APPLICATION_RDF_XML.equals(typeDesc)) ||
                     (APPLICATION_XML.equals(typeDesc))) {
                mimeType = APPLICATION_RDF_XML_TYPE;
            }
            else if (APPLICATION_TRIG.equals(typeDesc)) {
            	mimeType = APPLICATION_TRIG_TYPE;
            }
            else if (APPLICATION_TRIX.equals(typeDesc)) {
            	mimeType = APPLICATION_TRIX_TYPE;
            }
            else if (APPLICATION_NTRIPLES.equals(typeDesc)) {
        		mimeType = APPLICATION_NTRIPLES_TYPE;
        	}
        }
        if (mimeType == null) {
            throw new IllegalArgumentException(typeDesc);
        }
        return mimeType;
    }
}