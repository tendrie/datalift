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

package org.datalift.converter;


import java.net.URI;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.GregorianCalendar;

import static java.util.GregorianCalendar.*;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.xml.datatype.DatatypeFactory;

import org.openrdf.model.Literal;
import org.openrdf.model.ValueFactory;
import org.openrdf.repository.RepositoryConnection;

import org.datalift.fwk.project.Project;
import org.datalift.fwk.project.ProjectModule;
import org.datalift.fwk.project.Row;
import org.datalift.fwk.project.Source;
import org.datalift.fwk.project.SqlSource;
import org.datalift.fwk.project.Source.SourceType;
import org.datalift.fwk.rdf.Repository;
import org.datalift.fwk.util.StringUtils;


/**
 * A {@link ProjectModule project module} that performs SQL to RDF
 * conversion using
 * <a href="http://www.w3.org/TR/2011/WD-rdb-direct-mapping-20110324/">RDF
 * Direct Mapping</a> principles.
 *
 * @author lbihanic
 */
public class SqlDirectMapper extends BaseConverterModule
{
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    private final static String MODULE_NAME = "sqldirectmapper";

    //-------------------------------------------------------------------------
    // Class members
    //-------------------------------------------------------------------------

    private final static DatatypeFactory dtFactory;

    static {
        try {
            dtFactory = DatatypeFactory.newInstance();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /** Default constructor. */
    public SqlDirectMapper() {
        super(MODULE_NAME, SourceType.SqlSource);
    }

    //-------------------------------------------------------------------------
    // Web services
    //-------------------------------------------------------------------------

    @GET
    public Response getIndexPage(@QueryParam("project") URI projectId) {
        // Retrieve project.
        Project p = this.getProject(projectId);
        return Response.ok(this.newViewable("/sqlDirectMapper.vm", p)).build();
    }

    @POST
    public Response loadSourceData(@QueryParam("project") URI projectId,
                                   @QueryParam("source") URI sourceId,
                                   @FormParam("dest_title") String destTitle,
                                   @FormParam("dest_graph_uri") URI targetGraph)
                                                throws WebApplicationException {
        Response response = null;
        try {
            // Retrieve project.
            Project p = this.getProject(projectId);
            // Load input source.
            SqlSource in = (SqlSource)p.getSource(sourceId);
            // Convert CSV data and load generated RDF triples.
            this.convert(in, null, this.internalRepository, targetGraph);
            // Register new transformed RDF source.
            Source out = this.addResultSource(p, in, destTitle, targetGraph);
            // Display generated triples.
            response = this.redirectTo(p, out).build();
        }
        catch (Exception e) {
            this.handleInternalError(e);
        }
        return response;
    }

    //-------------------------------------------------------------------------
    // Specific implementation
    //-------------------------------------------------------------------------

    private void convert(SqlSource src, String keyColumn,
                                        Repository target, URI targetGraph) {
        final RepositoryConnection cnx = target.newConnection();
        try {
            final ValueFactory valueFactory = cnx.getValueFactory();

            // Prevent transaction commit for each triple inserted.
            cnx.setAutoCommit(false);
            // Clear target named graph, if any.
            org.openrdf.model.URI ctx = null;
            if (targetGraph != null) {
                ctx = valueFactory.createURI(targetGraph.toString());
                cnx.clear(ctx);
            }
            String baseUri = (targetGraph != null)?
                                            targetGraph.toString() + '/': "";
            // Build predicates URIs.
            int max = src.getColumnNames().size();
            org.openrdf.model.URI[] predicates = new org.openrdf.model.URI[max];
            int i = 0;
            for (String s : src.getColumnNames()) {
                if (! keyColumn.equals(s)) {
                    predicates[i] = valueFactory.createURI(
                                            baseUri + StringUtils.urlify(s));
                }
                i++;
            }
            // Load triples
            i = 0;
            for (Row<Object> row : src) {
                String key = (keyColumn != null)? row.getString(keyColumn):
                                                  String.valueOf(i);
                org.openrdf.model.URI subject =
                            valueFactory.createURI(baseUri + key); // + "#_";

                for (int j=0; j<max; j++) {
                    Object o = row.get(j);
                    if ((o != null) && (predicates[j] != null)) {
                        cnx.add(valueFactory.createStatement(
                                            subject, predicates[j],
                                            this.mapValue(o, valueFactory)),
                                ctx);
                    }
                    // Else: ignore cell.
                }
                i++;
            }
            cnx.commit();
        }
        catch (Exception e) {
            throw new TechnicalException("csv.conversion.failed", e);
        }
        finally {
            try { cnx.close(); } catch (Exception e) { /* Ignore */ }
        }
    }

    private Literal mapValue(Object o, ValueFactory valueFactory) {
        Literal v = null;

        if (o instanceof String) {
            v = valueFactory.createLiteral(o.toString());
        }
        else if (o instanceof Boolean) {
            v = valueFactory.createLiteral(((Boolean)o).booleanValue());
        }
        else if (o instanceof Byte) {
            v = valueFactory.createLiteral(((Byte)o).byteValue());
        }
        else if ((o instanceof Double) || (o instanceof Float)) {
            v = valueFactory.createLiteral(((Number)o).doubleValue());
        }
        else if ((o instanceof Integer) || (o instanceof Short)) {
            v = valueFactory.createLiteral(((Number)o).intValue());
        }
        else if (o instanceof Long) {
            v = valueFactory.createLiteral(((Long)o).longValue());
        }
        else if (o instanceof Date) {
            GregorianCalendar c = new GregorianCalendar();
            c.setTimeInMillis(((Date)o).getTime());

            v = valueFactory.createLiteral(
                    dtFactory.newXMLGregorianCalendarDate(this.getYear(c),
                                        c.get(MONTH) + 1, c.get(DAY_OF_MONTH),
                                        this.getTimeZoneOffsetInMinutes(c)));
        }
        else if (o instanceof Time) {
            GregorianCalendar c = new GregorianCalendar();
            c.setTimeInMillis(((Time)o).getTime());

            v = valueFactory.createLiteral(
                    dtFactory.newXMLGregorianCalendarTime(
                                        c.get(HOUR_OF_DAY), c.get(MINUTE),
                                        c.get(SECOND), c.get(MILLISECOND),
                                        this.getTimeZoneOffsetInMinutes(c)));
        }
        else if (o instanceof Timestamp) {
            GregorianCalendar c = new GregorianCalendar();
            c.setTimeInMillis(((Timestamp)o).getTime());

            v = valueFactory.createLiteral(
                                        dtFactory.newXMLGregorianCalendar(c));
        }
        return v;
    }

    private final int getYear(GregorianCalendar c) {
        int year = c.get(YEAR);
        if (c.get(ERA) == BC) {
            year = -year;
        }
        return year;
    }

    private final int getTimeZoneOffsetInMinutes(GregorianCalendar c) {
        return (c.get(ZONE_OFFSET) + c.get(DST_OFFSET)) / (60*1000);
    }
}