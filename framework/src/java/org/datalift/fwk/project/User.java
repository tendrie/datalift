/*
 * Copyright / Copr. 2010-2013 Atos - Public Sector France -
 * BS & Innovation for the DataLift project,
 * Contributor(s) : L. Bihanic, H. Devos, O. Ventura, M. Chetima
 *                  A. Valensi
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

package org.datalift.fwk.project;


/**
 * A user class to identify someone.
 * 
 * @author avalensi
 * 
 * TODO: See to move the BASE_USER_URI somewhere else.
 *
 */
public interface User {

	/**
	 * Base User URI.
	 */
	public static final String BASE_USER_URI = "http://www.datalift.org/user/"; 
	
	/**
	 * Get the RDF URI.
	 * @return the RDF URI
	 */
	String getUri();

	/**
	 * Get the identifier of the user.
	 * @return the identifier of the user.
	 */
	public String getIdentifier();

	/**
	 * Set the identifier.
	 * @param identifier   identifier to be setted.
	 */
	public void setIdentifier(String identifier);

	/**
	 * Get the agent who delegate.
	 * @return the agent who delegate.
	 */
	public String getActedOnBehalfOf();

	/**
	 * Set the agent who delegate
	 * @param actedOnBehalfOf   agent who delegate.
	 */
	public void setActedOnBehalfOf(String actedOnBehalfOf);

}