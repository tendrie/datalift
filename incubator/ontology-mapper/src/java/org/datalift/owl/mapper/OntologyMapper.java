package org.datalift.owl.mapper;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.openrdf.model.Resource;
import org.openrdf.model.URI;
import org.openrdf.model.vocabulary.RDF;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import static javax.ws.rs.core.Response.Status.*;
import static javax.ws.rs.core.HttpHeaders.*;

import org.datalift.fwk.BaseModule;
import org.datalift.fwk.Configuration;
import org.datalift.fwk.ResourceResolver;
import org.datalift.fwk.i18n.PreferredLocales;
import org.datalift.fwk.log.Logger;
import org.datalift.fwk.project.Project;
import org.datalift.fwk.project.ProjectManager;
import org.datalift.fwk.project.ProjectModule;
import org.datalift.fwk.project.Source;
import org.datalift.fwk.rdf.RdfFormat;
import org.datalift.fwk.rdf.RdfNamespace;
import org.datalift.fwk.rdf.RdfUtils;
import org.datalift.fwk.security.SecurityContext;
import org.datalift.fwk.util.StringUtils;
import org.datalift.fwk.util.io.FileUtils;
import org.datalift.fwk.util.web.Charsets;
import org.datalift.fwk.util.web.HttpDateFormat;
import org.datalift.fwk.view.TemplateModel;
import org.datalift.fwk.view.ViewFactory;
import org.datalift.owl.DatatypeProperty;
import org.datalift.owl.ObjectProperty;
import org.datalift.owl.Ontology;
import org.datalift.owl.OwlClass;
import org.datalift.owl.OwlObject;
import org.datalift.owl.OwlParser;
import org.datalift.owl.OwlProperty;
import org.datalift.owl.TechnicalException;
import org.datalift.sparql.query.ConstructQuery;
import org.datalift.sparql.query.UpdateQuery;

import static org.datalift.fwk.MediaTypes.*;
import static org.datalift.fwk.project.Source.SourceType.*;
import static org.datalift.fwk.util.StringUtils.*;


@Path(OntologyMapper.MODULE_NAME)
public class OntologyMapper extends BaseModule implements ProjectModule
{
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------

    /** The name of this module in the DataLift configuration. */
    public final static String MODULE_NAME = "ontology-mapper";
    /** The module position in the project view. */
    private final static int MODULE_POSITION = 6000;
    /** Base name of the resource bundle for converter GUI. */
    private final static String GUI_RESOURCES_BUNDLE = "resources";
    /** The regex to split string concatenation expressions. */
    private final static Pattern CONCAT_PATTERN =
                                                Pattern.compile("\\s+\\+\\s+");

    //-------------------------------------------------------------------------
    // Class members
    //-------------------------------------------------------------------------

    private final static Logger log = Logger.getLogger();

    //-------------------------------------------------------------------------
    // Instance members
    //-------------------------------------------------------------------------

    /** The DataLift project manager. */
    protected ProjectManager projectManager = null;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /** Default constructor. */
    public OntologyMapper() {
        super(MODULE_NAME);
    }

    //-------------------------------------------------------------------------
    // Module contract support
    //-------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public void postInit(Configuration configuration) {
        super.postInit(configuration);

        this.projectManager = configuration.getBean(ProjectManager.class);
        if (this.projectManager == null) {
            throw new TechnicalException("project.manager.not.available");
        }
    }

    //-------------------------------------------------------------------------
    // ProjectModule contract support
    //-------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public UriDesc canHandle(Project p) {
        UriDesc projectPage = null;
        try {
            if ((this.findSource(p, false) != null) &&
                (! p.getOntologies().isEmpty())) {
                try {
                    String label = PreferredLocales.get()
                                .getBundle(GUI_RESOURCES_BUNDLE, this)
                                .getString("ontology.mapper.button");

                    projectPage = new UriDesc(
                                    this.getName() + "?project=" + p.getUri(),
                                    HttpMethod.GET, label);
                    projectPage.setPosition(MODULE_POSITION);
                }
                catch (Exception e) {
                    throw new TechnicalException(e);
                }
            }
        }
        catch (Exception e) {
            log.fatal("Failed to check status of project {}: {}", e,
                                                p.getUri(), e.getMessage());
        }
        return projectPage;
    }

    //-------------------------------------------------------------------------
    // Web services
    //-------------------------------------------------------------------------

    @GET
    @Produces({ TEXT_HTML, APPLICATION_XHTML_XML })
    public Response getIndexPage(@QueryParam("project") java.net.URI projectId) {
        Response response = null;
        try {
            // Retrieve project.
            Project p = this.getProject(projectId);
            // Display conversion configuration page.
            TemplateModel view = this.newView("mapper.vm", p);
            view.put("srcType", TransformedRdfSource);
            view.put("login", SecurityContext.getUserPrincipal());
            response = Response.ok(view, TEXT_HTML_UTF8).build();
        }
        catch (IllegalArgumentException e) {
            TechnicalException error =
                            new TechnicalException("ws.invalid.param.error",
                                                   "project", projectId);
            this.sendError(Status.BAD_REQUEST, error.getLocalizedMessage());
        }
        return response;
    }

    @GET
    @Path("ontology")
    @Produces(APPLICATION_JSON)
    public Response parseOntology(@QueryParam("src") String src) {
        if (! StringUtils.isSet(src)) {
            this.throwInvalidParamError("src", null);
        }
        Response response = null;
        try {
            URL u = new URL(src);
            String path = u.getPath();
            if (path.charAt(0) == '/') {
                path = path.substring(1);
            }
            File f = new File(Configuration.getDefault().getTempStorage(),
                              MODULE_NAME + '/' + path);
            long now = System.currentTimeMillis();
            if ((! f.exists()) || (f.lastModified() < now)) {
                // Compute HTTP Accept header.
                Map<String,String> headers = new HashMap<String,String>();
                headers.put(ACCEPT, this.getRdfAcceptHeader());
                if (f.exists()) {
                    headers.put(IF_MODIFIED_SINCE,
                                HttpDateFormat.formatDate(f.lastModified()));
                }
                // Make sure parent directories exist.
                f.getParentFile().mkdirs();
                // Retrieve file from source URL.
                FileUtils.DownloadInfo info = FileUtils.save(u, null, headers, f);
                f.setLastModified((info.expires > 0L)? info.expires: now);
                // Extract RDF format from MIME type to force file suffix.
                RdfFormat fmt = RdfFormat.find(info.mimeType.toString(false));
                if (fmt == null) {
                    throw new TechnicalException("invalid.remote.mime.type",
                                                 info.mimeType);
                }
                // Ensure file extension is present to allow RDF syntax
                // detection in future cache accesses.
                String ext = "." + fmt.getFileExtension();
                if (! f.getName().endsWith(ext)) {
                    File newFile = new File(f.getCanonicalPath() + ext);
                    f.renameTo(newFile);
                    f = newFile;
                }
                // Mark file as to be deleted upon JVM termination.
                f.deleteOnExit();
            }
            // Parse ontology.
            Ontology o = new OwlParser().parse(f, src);
            // Return JSON representation of OWL ontology.
            response = Response.ok(new OntologyJsonStreamingOutput(o),
                                   APPLICATION_JSON_UTF8).build();
        }
        catch (Exception e) {
            this.handleInternalError(e);
        }
        return response;
    }

    @POST
    @Path("preview")
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces(TEXT_PLAIN)
    public String getSparqlPreview(@FormParam("sourceGraph") String sourceGraph,
                                   @FormParam("targetName") String targetName,
                                   @FormParam("targetGraph") String targetGraph,
                                   @FormParam("ontology") String ontologyJson,
                                   @FormParam("mapping") String mappingJson)
                                                throws WebApplicationException {
        log.info("Mapping: {}", mappingJson);
        sourceGraph = RdfUtils.getBaseUri(sourceGraph);
        if (isSet(targetGraph)) {
            targetGraph = RdfUtils.getBaseUri(targetGraph);
        }
        Gson gson = new Gson();
        OntologyDesc o = gson.fromJson(ontologyJson, OntologyDesc.class);
        MappingDesc  m = gson.fromJson(mappingJson, MappingDesc.class);
        UpdateQuery query = new ConstructQuery();
        String targetNsUri = query.uri(m.types.get(0)).getNamespace();
        query.prefix("src", sourceGraph)
             .prefix(o.name, targetNsUri);
        Resource node = query.variable(m.name);
        String s = this.mapNode(query, m, node, node).toString();
        log.info("Query: {}", s);
        return s;
    }

    /**
     * Traps accesses to module static resources and redirect them
     * toward the default {@link ResourceResolver} for resolution.
     * @param  path        the relative path of the module static
     *                     resource being accessed.
     * @param  uriInfo     the request URI data (injected).
     * @param  request     the JAX-RS request object (injected).
     * @param  acceptHdr   the HTTP "Accept" header value.
     *
     * @return a {@link Response JAX-RS response} to download the
     *         content of the specified public resource.
     */
    @GET
    @Path("static/{path: .*$}")
    public Object getStaticResource(@PathParam("path") String path,
                                    @Context UriInfo uriInfo,
                                    @Context Request request,
                                    @HeaderParam(ACCEPT) String acceptHdr) {
        return Configuration.getDefault()
                            .getBean(ResourceResolver.class)
                            .resolveModuleResource(this.getName(),
                                                   uriInfo, request, acceptHdr);
    }

    //-------------------------------------------------------------------------
    // Specific implementation
    //-------------------------------------------------------------------------

    /**
     * Retrieves a {@link Project} using its URI.
     * @param  projectId   the project URI.
     *
     * @return the project.
     * @throws TechnicalException if the project does not exist.
     */
    private final Project getProject(java.net.URI projectId) {
        Project p = this.projectManager.findProject(projectId);
        if (p == null) {
            throw new IllegalArgumentException(projectId.toString());
        }
        return p;
    }

    /**
     * Searches the specified project for a source matching the expected
     * input source type of the module.
     * @param  p          the project.
     * @param  findLast   whether to return the last matching source or
     *                    the first.
     * @return a matching source of <code>null</code> if no matching
     *         source was found.
     */
    private Source findSource(Project p, boolean findLast) {
        if (p == null) {
            throw new IllegalArgumentException("p");
        }
        Source src = null;
        for (Source s : p.getSources()) {
            if (s.getType() == TransformedRdfSource) {
                src = s;
                if (! findLast) break;
                // Else: continue to get last source of type in project...
            }
        }
        return src;
    }

    private String getRdfAcceptHeader() {
        StringBuilder buf = new StringBuilder();
        for (RdfFormat fmt : RdfFormat.values()) {
            boolean first = true;
            for (MediaType m : fmt.mimeTypes) {
                if (first) {
                    // Preferred MIME type (q=1.0).
                    buf.append(m);
                    first = false;
                }
                else {
                    // Secondary MIME types.
                    buf.append(", ").append(m).append("; q=0.5");
                }
            }
        }
        return buf.toString();
    }

    private UpdateQuery mapNode(UpdateQuery query, MappingDesc m,
                                Resource from, Resource node) {
        Map<URI,String> mapping = new HashMap<URI,String>();
        if (m.types.isEmpty()) {
            // Simple property.
            URI u = query.uri(m.predicate);
            query.prefixFor(u.getNamespace());
            mapping.put(u, this.mapFunctions(m.value, query));
        }
        else {
            // RDF node.
            if (isSet(m.predicate)) {
                Resource o = query.blankNode();
                query.triple(node, query.uri(m.predicate), o);
                node = o;
            }
            // Else: No predicate linking node to parent? Assume root node.

            for (String t : m.types) {
                // Add RDF types.
                query.rdfType(node, query.uri(t));
            }
            if (isSet(m.value)) {
                // Add value mapping.
                mapping.put(RDF.VALUE, this.mapFunctions(m.value, query));
            }
        }
        if (! mapping.isEmpty()) {
            // Mappings present (triples & where clauses) => Insert in query.
            query.map(from, node, mapping);
        }
        // Process child mappings.
        for (MappingDesc child : m.children) {
            this.mapNode(query, child, from, node);
        }
        return query;
    }

    private String mapFunctions(String expr, UpdateQuery query) {
        if (isSet(expr)) {
            if (CONCAT_PATTERN.matcher(expr).find()) {
                StringBuilder buf = new StringBuilder(expr.length() + 8);
                buf.append("CONCAT(");
                for (String s : CONCAT_PATTERN.split(expr.trim())) {
                    buf.append(this.resolvePrefixes(s, query)).append(',');
                }
                buf.setLength(buf.length() - 1);
                expr = buf.append(')').toString();
            }
            else {
                expr = this.resolvePrefixes(expr, query);
            }
        }
        return expr;
    }

    private String resolvePrefixes(String expr, UpdateQuery query) {
        if ((isSet(expr)) && (expr.charAt(0) != '<')) {
            int i = expr.indexOf(':');
            if (i > 0) {
                RdfNamespace ns = RdfNamespace.findByPrefix(expr.substring(0, i));
                if (ns != null) {
                    // Add prefix for matched well-known namespace.
                    query.prefix(ns.prefix, ns.uri);
                }
            }
            // Else: no known prefix matches.
        }
        return expr;
    }

    /**
     * Return a model for the specified template view, populated with
     * the specified model object.
     * <p>
     * The template name shall be relative to the module, the module
     * name is automatically prepended.</p>
     * @param  templateName   the relative template name.
     * @param  it             the model object to pass on to the view.
     *
     * @return a populated template model.
     */
    protected final TemplateModel newView(String templateName, Object it) {
        return ViewFactory.newView(
                                "/" + this.getName() + '/' + templateName, it);
    }

    private void throwInvalidParamError(String name, Object value) {
        TechnicalException error = (value != null)?
                new TechnicalException("ws.invalid.param.error", name, value):
                new TechnicalException("ws.missing.param", name);
        this.sendError(BAD_REQUEST, error.getLocalizedMessage());
    }

    private void handleInternalError(Exception e)
                                                throws WebApplicationException {
        TechnicalException error = null;
        if (e instanceof WebApplicationException) {
            throw (WebApplicationException)e;
        }
        else if (e instanceof FileNotFoundException) {
            this.sendError(NOT_FOUND, e.getLocalizedMessage());
        }
        else if (e instanceof TechnicalException) {
            error = (TechnicalException)e;
        }
        else {
            error = new TechnicalException(
                            "ws.internal.error", e, e.getLocalizedMessage());
        }
        log.fatal(e.getMessage(), e);
        this.sendError(INTERNAL_SERVER_ERROR, error.getLocalizedMessage());
    }

    public final static class OntologyDesc
    {
        public String name;
        public String uri;
    }
    public final static class MappingDesc
    {
        public String name;
        public String predicate;
        public String value;
        public List<String> types = new LinkedList<String>();
        public Collection<MappingDesc> children = new LinkedList<MappingDesc>();
    }

    private final static class OntologyJsonStreamingOutput
                                                    implements StreamingOutput
    {
        private final Ontology ontology;

        public OntologyJsonStreamingOutput(Ontology ontology) {
            this.ontology = ontology;
        }

        @Override
        public void write(OutputStream output)
                                throws IOException, WebApplicationException {
            OwlSerializer owlSerializer = new OwlSerializer();
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(OwlClass.class, owlSerializer)
                    .registerTypeAdapter(OwlProperty.class, owlSerializer)
                    .registerTypeAdapter(ObjectProperty.class, owlSerializer)
                    .registerTypeAdapter(DatatypeProperty.class, owlSerializer)
                    .registerTypeAdapter(java.net.URI.class, new UriSerializer())
                    .setPrettyPrinting().create();
            Writer w = new OutputStreamWriter(output, Charsets.UTF_8);
            gson.toJson(this.ontology, w);
            w.flush();
        }
    }

    private final static class OwlSerializer implements JsonSerializer<OwlObject>
    {
        private boolean inOwl = false;

        public OwlSerializer() {
            super();
        }

        public JsonElement serialize(OwlObject src, Type type,
                                     JsonSerializationContext context) {
            JsonElement e = null;
            if (inOwl) {
                e = new JsonPrimitive(src.uri());
            }
            else {
                inOwl = true;
                if (src instanceof OwlClass) {
                    e = this.serialize((OwlClass)src);
                }
                else if (src instanceof OwlProperty) {
                    e = this.serialize((OwlProperty)src);
                }
                else {
                    e = this.serialize(src);
                }
                inOwl = false;
            }
            return e;
        }

        private JsonObject serialize(OwlObject o) {
            JsonObject e = new JsonObject();
            // e.addProperty("uri", src.uri);
            e.addProperty("type", o.getClass().getSimpleName());
            e.addProperty("name", o.name);
            if (isSet(o.desc)) {
                e.addProperty("desc", o.desc);
            }
            return e;
        }

        private JsonObject serialize(OwlClass c) {
            JsonObject e = this.serialize((OwlObject)c);
            this.add(c.parents(), "parents", e);
            this.add(c.subclasses(), "subclasses", e);
            this.add(c.disjoints(), "disjoints", e);
            this.add(c.properties(true), "properties", e);
            if (c.union()) {
                e.addProperty("union", Boolean.TRUE);
            }
            return e;
        }

        private JsonObject serialize(OwlProperty p) {
            JsonObject e = this.serialize((OwlObject)p);
            Collection<OwlClass> ranges = p.ranges();
            if (! ranges.isEmpty()) {
                this.add(p.ranges(),  "ranges", e);
            }
            else {
                if (p.type() != null) {
                    JsonArray x = new JsonArray();
                    x.add(new JsonPrimitive(p.type().stringValue()));
                    e.add("ranges", x);
                }
            }
            this.add(p.domains(), "domains", e);
            return e;
        }

        private void add(Collection<? extends OwlObject> c,
                                                String name, JsonObject e) {
            if ((c != null) && (! c.isEmpty())) {
                JsonArray x = new JsonArray();
                for (OwlObject o : c) {
                    x.add(new JsonPrimitive(o.uri()));
                }
                e.add(name, x);
            }
        }
    }

    private final static class UriSerializer
                                implements JsonSerializer<org.openrdf.model.URI>
    {
        public UriSerializer() {
            super();
        }

        public JsonElement serialize(org.openrdf.model.URI src, Type typeOfSrc,
                                     JsonSerializationContext context) {
            return new JsonPrimitive(src.stringValue());
        }
    }

    public static void main(String[] args) throws Exception {
        OntologyMapper m = new OntologyMapper();
        System.out.println(
        m.getSparqlPreview("http://localhost:9091/datalift/project/kiosques/source/kiosques-ouverts-a-paris-csv-rdf-1-rdf-1",
                           "A source",
                           "http://localhost:9091/datalift/project/kiosques/source/kiosques-ouverts-a-paris-csv-rdf-1-rdf-1-onto",
                           "{\"name\":\"vCard\",\"uri\":\"http://www.w3.org/2006/vcard/ns#\"}",
                           // "{\"types\":[\"http://www.w3.org/2006/vcard/ns#VCard\"],\"children\":[{\"predicate\":\"http://www.w3.org/2006/vcard/ns#adr\",\"types\":[\"http://www.w3.org/2006/vcard/ns#Address\"],\"children\":[{\"predicate\":\"http://www.w3.org/2006/vcard/ns#street-address\",\"value\":\"adresse\"}]}]}"));
                           "{\"types\":[\"http://www.w3.org/2006/vcard/ns#Address\"],\"children\":[{\"predicate\":\"http://www.w3.org/2006/vcard/ns#street-address\",\"value\":\"adresse\"}]}"));

    }
}