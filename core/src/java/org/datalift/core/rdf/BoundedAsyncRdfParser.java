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

package org.datalift.core.rdf;


import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.openrdf.model.Statement;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParser;
import org.openrdf.rio.helpers.RDFHandlerBase;

import org.datalift.core.TechnicalException;
import org.datalift.fwk.rdf.RdfUtils;
import org.datalift.fwk.util.CloseableIterator;


/**
 * A utility class for performing an asynchronous RDF parse populating
 * a {@link Statement statement} iterator but limiting the number of
 * statements stored in memory. The iterator client speed controls the
 * parser speed.
 *
 * @author lbihanic
 */
public final class BoundedAsyncRdfParser
{
    //-------------------------------------------------------------------------
    // Class members
    //-------------------------------------------------------------------------

    private final static ExecutorService threadPool =
                                            Executors.newCachedThreadPool();

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /** Default constructor, private on purpose. */
    private BoundedAsyncRdfParser() {
        throw new UnsupportedOperationException();
    }

    //-------------------------------------------------------------------------
    // Specific implementation
    //-------------------------------------------------------------------------

    /**
     * Parses the specified RDF data stream.
     * @param  in         the RDF data stream to parse.
     * @param  mimeType   the expected type of the data.
     * @param  baseUri    the base URI to translate relative URIs.
     *
     * @return an iterator on the parsed RDF statements.
     *
     * @see    #parse(InputStream, String, String, int)
     */
    public static CloseableIterator<Statement> parse(InputStream in,
                                        String mimeType, String baseUri) {
        return parse(in, mimeType, baseUri, 100);
    }

    /**
     * Parses the specified RDF data stream.
     * @param  in           the RDF data stream to parse.
     * @param  mimeType     the expected type of the data.
     * @param  baseUri      the base URI to translate relative URIs.
     * @param  bufferSize   the maximum number of RDF statements the
     *                      iterator can buffer.
     *
     * @return an iterator on the parsed RDF statements.
     *
     * @see    #parse(InputStream, String, String, int)
     */
    public static CloseableIterator<Statement> parse(final InputStream in,
                                        String mimeType, final String baseUri,
                                        int bufferSize) {
        final RDFParser parser = RdfUtils.newRdfParser(mimeType);
        // Use a blocking queue to control the memory alloted to the
        // being-read RDF statements. Let the producer (RDF parser) be
        // ahead of the consumer (iterator client) by bufferSize statements.
        final BlockingQueue<Statement> statements =
                                new ArrayBlockingQueue<Statement>(bufferSize);
        // Parse RDF data in a separate thread, queuing the read statements.
        final Future<Void> f = threadPool.submit(new Callable<Void>()
            {            
                @Override
                public Void call() {
                    try {
                        parser.setRDFHandler(new RDFHandlerBase()
                            {
                                @Override
                                public void handleStatement(Statement st)
                                                    throws RDFHandlerException {
                                    try {
                                        statements.put(st);
                                    }
                                    catch (InterruptedException e) {
                                        throw new RDFHandlerException(e);
                                    }
                                }
                            });
                        parser.parse(in, (baseUri != null)? baseUri: "");
                    }
                    catch (Exception e) {
                        new TechnicalException(
                                        "rdf.parse.error", e, e.getMessage());
                    }
                    return null;
                }
            });

        return new CloseableIterator<Statement>()
            {
                private Statement current = this.getNextStatement();

                @Override
                public boolean hasNext() {
                    return (this.current != null);
                }

                @Override
                public Statement next() {
                    Statement next = this.current;
                    this.current = this.getNextStatement();
                    return next;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() {
                    if (! f.isDone()) {
                        // Abort RDF parse.
                        f.cancel(true);
                    }
                    // Wait for task termination.
                    try {
                        f.get();
                    }
                    catch (ExecutionException e) {
                        throw (RuntimeException)(e.getCause());
                    }
                    catch (Exception e) { /* Ignore... */ }
                }

                private Statement getNextStatement() {
                    Statement stmt = null;
                    // Get next statement from queue.
                    if ((statements.peek() == null) && (f.isDone())) {
                        // Parse complete & queue empty.
                        this.close();
                    }
                    else {
                        // Consume next statement from queue.
                        try {
                            stmt = statements.take();
                        }
                        catch (InterruptedException e) {
                            // Thread interrupted.
                            throw new RuntimeException(e);
                        }
                    }
                    return stmt;
                }
            };
    }
}