package org.datalift.core.prov;

import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;

import org.datalift.core.project.BaseRdfEntity;
import org.datalift.core.util.JsonStringParameters;
import org.datalift.fwk.project.Project;
import org.datalift.fwk.prov.Event;
import org.datalift.fwk.prov.EventType;

import com.clarkparsia.empire.annotation.NamedGraph;
import com.clarkparsia.empire.annotation.RdfId;
import com.clarkparsia.empire.annotation.RdfProperty;
import com.clarkparsia.empire.annotation.RdfsClass;

@Entity
@RdfsClass("datalift:Event")
@NamedGraph(type = NamedGraph.NamedGraphType.Static, value="http://www.datalift.org/core/project-history")
public class EventImpl extends BaseRdfEntity implements Event{
    
    @RdfId
    private URI uri;
    @RdfProperty("datalift:operation")
    private URI operation;
    @RdfProperty("datalift:parameters")
    private String parameters;
    private JsonStringParameters param;
    @RdfProperty("datalift:project")
    private URI projectId;
    private Project project = null;
    @RdfProperty("datalift:eventType")
    private URI type;
    @RdfProperty("prov:startedAtTime")
    private Date start;
    @RdfProperty("prov:endedAtTime")
    private Date end;
    @RdfProperty("prov:wasAssociatedWith")
    private URI agent;
    @RdfProperty("prov:influenced")
    private URI influenced;
    @RdfProperty("prov:used")
    @OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.ALL })
    private Collection<URI> used = new LinkedList<URI>();
    @RdfProperty("prov:wasInformedBy")
    private URI informer;

    
    public EventImpl(){
        //NOP
    }
    
    public EventImpl(URI id,
            Project project,
            URI operation,
            Map<String, String> parameters,
            EventType eventType,
            Date start,
            Date end,
            URI agent,
            URI influenced,
            URI informer,
            URI... used){
        this.operation = operation;
        if(parameters != null){
            this.param = new JsonStringParameters(parameters);
        } else {
            this.param = null;
            this.parameters = null;
        }
        this.type = eventType.getUri();
        this.start = start;
        this.end = end;
        this.agent = agent;
        this.influenced = influenced;
        for(URI i : used)
            this.used.add(i);
        this.uri = id;
        this.project = project;
        this.informer = informer;
        if(project != null)
            this.projectId = URI.create(project.getUri());
    }
    
    
    @Override
    public URI getInformer(){
        return this.informer;
    }
    
    @Override
    public URI getUri() {
        return this.uri;
    }

    @Override
    public URI getOperation() {
        return this.operation;
    }

    @Override
    public Map<String, String> getParameters() {
        if(this.param == null && this.parameters != null){
            this.param = new JsonStringParameters(this.parameters);
            return this.param.getParametersMap();
        }
        return null;
    }

    @Override
    public EventType getEventType() {
        return EventType.getInstance(this.type);
    }

    @Override
    public Date getStartTime() {
        return this.start;
    }

    @Override
    public Date getEndTime() {
        return this.end;
    }

    @Override
    public URI getAgent() {
        return this.agent;
    }

    @Override
    public URI getInfluenced() {
        return this.influenced;
    }

    @Override
    public Collection<URI> getUsed() {
        return new LinkedList<URI>(this.used);
    }

    @Override
    protected void setId(String id) {
        this.uri = URI.create(id);
    }

    @Override
    public Project getProject() {
        return this.project;
    }
    
    @Override
    public boolean equals(Object o){
        if(o == null)
            return false;
        if(!(o instanceof Event))
            return false;
        if(((Event)o).getUri().equals(this.uri))
            return true;
        return false;
    }
    
    public void addUsed(URI used){
        this.used.add(used);
    }
    
    public void setInfluenced(URI influenced){
        this.influenced = influenced;
    }
}