package org.datalift.core.project;

import javax.persistence.Entity;

import org.datalift.fwk.project.Project;
import org.datalift.fwk.project.WfsSource;
import org.datalift.fwk.project.Source.SourceType;

import com.clarkparsia.empire.annotation.RdfsClass;

@Entity
@RdfsClass("datalift:wfsSource")
public class WfsSourceImpl extends BaseServiceSource
							implements WfsSource{

	public WfsSourceImpl() {
		super(SourceType.WfsSource);
		// TODO Auto-generated constructor stub
	}
	/**
	 * Creates a new Shapefile source with the specified identifier and
	 * owning project.
	 * @param  uri       the source unique identifier (URI) or
	 *                   <code>null</code> if not known at this stage.
	 * @param  project   the owning project or <code>null</code> if not
	 *                   known at this stage.
	 *
	 * @throws IllegalArgumentException if either <code>uri</code> or
	 *         <code>project</code> is <code>null</code>.
	 */
	public WfsSourceImpl(String uri, Project project) {
		super(SourceType.WfsSource, uri, project);
	}

	@Override
	public String getServerType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setServerType(String server) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isCompliantInspire() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setCompliantInspire() {
		// TODO Auto-generated method stub
		
	}

}