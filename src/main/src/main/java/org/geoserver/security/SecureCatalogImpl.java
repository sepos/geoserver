/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security;

import static org.geoserver.catalog.Predicates.acceptAll;
import static org.geoserver.catalog.Predicates.or;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogFacade;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WMSStoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.impl.AbstractDecorator;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.security.decorators.SecuredCoverageInfo;
import org.geoserver.security.decorators.SecuredCoverageStoreInfo;
import org.geoserver.security.decorators.SecuredDataStoreInfo;
import org.geoserver.security.decorators.SecuredFeatureTypeInfo;
import org.geoserver.security.decorators.SecuredLayerGroupInfo;
import org.geoserver.security.decorators.SecuredLayerInfo;
import org.geoserver.security.decorators.SecuredWMSLayerInfo;
import org.geoserver.security.impl.DataAccessRuleDAO;
import org.geoserver.security.impl.DefaultDataAccessManager;
import org.geotools.filter.expression.InternalVolatileFunction;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.sort.SortBy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

/**
 * Wraps the catalog and applies the security directives provided by a {@link ResourceAccessManager}
 * or a {@link DataAccessManager} registered in the Spring application context
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class SecureCatalogImpl extends AbstractDecorator<Catalog> implements Catalog {

    protected ResourceAccessManager accessManager;
    
    public SecureCatalogImpl(Catalog catalog) throws Exception {
        this(catalog, lookupResourceAccessManager());
    }
    
    public String getId() {
        return delegate.getId();
    }
    
    public ResourceAccessManager getResourceAccessManager() {
        return accessManager;
    }

    static ResourceAccessManager lookupResourceAccessManager() throws Exception {
        ResourceAccessManager manager = GeoServerExtensions.bean(ResourceAccessManager.class);
        if (manager == null) {
            DataAccessManager daManager = lookupDataAccessManager();
            manager = new DataAccessManagerAdapter(daManager);
        } 
        CatalogFilterAccessManager lwManager = new CatalogFilterAccessManager();
        lwManager.setDelegate(manager);
        return lwManager;
    }

    static DataAccessManager lookupDataAccessManager() throws Exception {
        DataAccessManager manager = GeoServerExtensions.bean(DataAccessManager.class);
        if (manager == null) {
            manager = new DefaultDataAccessManager(GeoServerExtensions.bean(DataAccessRuleDAO.class));
        } else {
            if (manager instanceof DataAccessManagerWrapper) {
                ((DataAccessManagerWrapper)manager).setDelegate(
                    new DefaultDataAccessManager(GeoServerExtensions.bean(DataAccessRuleDAO.class)));
            }
        }
        return manager;
    }

    public SecureCatalogImpl(Catalog catalog, ResourceAccessManager manager) {
        super(catalog);
        this.accessManager = manager;
    }

    // -------------------------------------------------------------------
    // SECURED METHODS
    // -------------------------------------------------------------------

    public CoverageInfo getCoverage(String id) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverage(id));
    }

    public CoverageInfo getCoverageByName(String ns, String name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(ns, name));
    }

    public CoverageInfo getCoverageByName(NamespaceInfo ns, String name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(ns, name));
    }
    
    public CoverageInfo getCoverageByName(Name name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(name));
    }
    
    public CoverageInfo getCoverageByName(String name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(name));
    }

    public List<CoverageInfo> getCoverages() {
        return filterResources(user(), delegate.getCoverages());
    }

    public List<CoverageInfo> getCoveragesByNamespace(NamespaceInfo namespace) {
        return filterResources(user(), delegate.getCoveragesByNamespace(namespace));
    }
    
    public List<CoverageInfo> getCoveragesByCoverageStore(
            CoverageStoreInfo store) {
        return filterResources(user(), delegate.getCoveragesByCoverageStore(store));
    }
    
    public CoverageInfo getCoverageByCoverageStore(
            CoverageStoreInfo coverageStore, String name) {
        return checkAccess(user(), delegate.getCoverageByCoverageStore(coverageStore, name));
    }

    public List<CoverageInfo> getCoveragesByStore(CoverageStoreInfo store) {
        return filterResources(user(), delegate.getCoveragesByStore(store));
    }
    
    public CoverageStoreInfo getCoverageStore(String id) {
        return checkAccess(user(), delegate.getCoverageStore(id));
    }

    public CoverageStoreInfo getCoverageStoreByName(String name) {
        return checkAccess(user(), delegate.getCoverageStoreByName(name));
    }
    
    public CoverageStoreInfo getCoverageStoreByName(String workspaceName,
            String name) {
        return checkAccess(user(), delegate.getCoverageStoreByName(workspaceName,name));
    }
    
    public CoverageStoreInfo getCoverageStoreByName(WorkspaceInfo workspace,
            String name) {
        return checkAccess(user(), delegate.getCoverageStoreByName(workspace,name));
    }
    
    public List<CoverageStoreInfo> getCoverageStoresByWorkspace(
            String workspaceName) {
        return filterStores(user(),delegate.getCoverageStoresByWorkspace(workspaceName));
    }
    
    public List<CoverageStoreInfo> getCoverageStoresByWorkspace(
            WorkspaceInfo workspace) {
        return filterStores(user(),delegate.getCoverageStoresByWorkspace(workspace));
    }

    public List<CoverageStoreInfo> getCoverageStores() {
        return filterStores(user(), delegate.getCoverageStores());
    }

    public DataStoreInfo getDataStore(String id) {
        return checkAccess(user(), delegate.getDataStore(id));
    }

    public DataStoreInfo getDataStoreByName(String name) {
        return checkAccess(user(), delegate.getDataStoreByName(name));
    }
    
    public DataStoreInfo getDataStoreByName(String workspaceName, String name) {
        return checkAccess(user(), delegate.getDataStoreByName(workspaceName,name));
    }
    
    public DataStoreInfo getDataStoreByName(WorkspaceInfo workspace, String name) {
        return checkAccess(user(), delegate.getDataStoreByName(workspace,name)) ;
    }
    
    public List<DataStoreInfo> getDataStoresByWorkspace(String workspaceName) {
        return filterStores(user(), delegate.getDataStoresByWorkspace(workspaceName));
    }

    public List<DataStoreInfo> getDataStoresByWorkspace(WorkspaceInfo workspace) {
        return filterStores(user(), delegate.getDataStoresByWorkspace(workspace));
    }

    public List<DataStoreInfo> getDataStores() {
        return filterStores(user(), delegate.getDataStores());
    }

    public NamespaceInfo getDefaultNamespace() {
        return delegate.getDefaultNamespace();
    }

    public WorkspaceInfo getDefaultWorkspace() {
        return delegate.getDefaultWorkspace();
    }

    public FeatureTypeInfo getFeatureType(String id) {
        return checkAccess(user(), delegate.getFeatureType(id));
    }

    public FeatureTypeInfo getFeatureTypeByName(String ns, String name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(ns, name));
    }
    
    public FeatureTypeInfo getFeatureTypeByName(NamespaceInfo ns, String name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(ns,name));
    }

    public FeatureTypeInfo getFeatureTypeByName(Name name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(name));
    }
    
    public FeatureTypeInfo getFeatureTypeByName(String name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(name));
    }

    public List<FeatureTypeInfo> getFeatureTypes() {
        return filterResources(user(), delegate.getFeatureTypes());
    }

    public List<FeatureTypeInfo> getFeatureTypesByNamespace(NamespaceInfo namespace) {
        return filterResources(user(), delegate.getFeatureTypesByNamespace(namespace));
    }

    public FeatureTypeInfo getFeatureTypeByStore(DataStoreInfo dataStore,
            String name) {
        return checkAccess(user(), delegate.getFeatureTypeByStore(dataStore , name));
    }
    public FeatureTypeInfo getFeatureTypeByDataStore(DataStoreInfo dataStore,
            String name) {
        return checkAccess(user(), delegate.getFeatureTypeByDataStore(dataStore , name));
    }
    
    public List<FeatureTypeInfo> getFeatureTypesByStore(DataStoreInfo store) {
        return filterResources(user(), delegate.getFeatureTypesByStore(store));
    }
    public List<FeatureTypeInfo> getFeatureTypesByDataStore(DataStoreInfo store) {
        return filterResources(user(), delegate.getFeatureTypesByDataStore(store));
    }

    public LayerInfo getLayer(String id) {
        return checkAccess(user(), delegate.getLayer(id));
    }

    public LayerInfo getLayerByName(String name) {
        return checkAccess(user(), delegate.getLayerByName(name));
    }
    
    public LayerInfo getLayerByName(Name name) {
        return checkAccess(user(), delegate.getLayerByName(name));
    }

    public LayerGroupInfo getLayerGroup(String id) {
        return checkAccess(user(), delegate.getLayerGroup(id));
    }

    public LayerGroupInfo getLayerGroupByName(String name) {
        return checkAccess(user(), delegate.getLayerGroupByName(name));
    }

    public LayerGroupInfo getLayerGroupByName(String workspaceName, String name) {
        return checkAccess(user(), delegate.getLayerGroupByName(workspaceName, name));
    }

    public LayerGroupInfo getLayerGroupByName(WorkspaceInfo workspace,
            String name) {
        return checkAccess(user(), delegate.getLayerGroupByName(workspace, name));
    }

    public List<LayerGroupInfo> getLayerGroups() {
        return filterGroups(user(), delegate.getLayerGroups());
    }

    public List<LayerGroupInfo> getLayerGroupsByWorkspace(String workspaceName) {
        return filterGroups(user(), delegate.getLayerGroupsByWorkspace(workspaceName));
    }

    public List<LayerGroupInfo> getLayerGroupsByWorkspace(
            WorkspaceInfo workspace) {
        return filterGroups(user(), delegate.getLayerGroupsByWorkspace(workspace));
    }

    public List<LayerInfo> getLayers() {
        return filterLayers(acceptAll());
    }

    public List<LayerInfo> getLayers(ResourceInfo resource) {
        return filterLayers(Predicates.equal("resource.id", resource.getId()));
    }
    
    public List<LayerInfo> getLayers(StyleInfo style) {

        String id = style.getId();
        Filter filter = or(Predicates.equal("defaultStyle.id", id),
                Predicates.equal("styles.id", id));

        return filterLayers(filter);
    }

    private List<LayerInfo> filterLayers(final Filter filter) {

        CloseableIterator<LayerInfo> iterator;
        iterator = list(LayerInfo.class, filter);
        try {
            return ImmutableList.copyOf(iterator);
        } finally {
            iterator.close();
        }
    }

    public NamespaceInfo getNamespace(String id) {
        return checkAccess(user(), delegate.getNamespace(id));
    }

    public NamespaceInfo getNamespaceByPrefix(String prefix) {
        return checkAccess(user(), delegate.getNamespaceByPrefix(prefix));
    }

    public NamespaceInfo getNamespaceByURI(String uri) {
        return checkAccess(user(), delegate.getNamespaceByURI(uri));
    }

    public List<NamespaceInfo> getNamespaces() {
        return filterNamespaces(user(), delegate.getNamespaces());
    }

    public <T extends ResourceInfo> T getResource(String id, Class<T> clazz) {
        return checkAccess(user(), delegate.getResource(id, clazz));
    }

    public <T extends ResourceInfo> T getResourceByName(Name name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(name, clazz));
    }
    
    public <T extends ResourceInfo> T getResourceByName(String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(name, clazz));
    }

    public <T extends ResourceInfo> T getResourceByName(NamespaceInfo ns,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(ns, name, clazz)) ;
    }

    public <T extends ResourceInfo> T getResourceByName(String ns, String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(ns, name, clazz));
    }

    public <T extends ResourceInfo> List<T> getResources(Class<T> clazz) {
        return filterResources(user(), delegate.getResources(clazz));
    }

    public <T extends ResourceInfo> List<T> getResourcesByNamespace(NamespaceInfo namespace,
            Class<T> clazz) {
        return filterResources(user(), delegate.getResourcesByNamespace(namespace, clazz));
    }

    public <T extends ResourceInfo> List<T> getResourcesByNamespace(
            String namespace, Class<T> clazz) {
        return filterResources(user(), delegate.getResourcesByNamespace(namespace, clazz));
    }
    
    public <T extends ResourceInfo> T getResourceByStore(StoreInfo store,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByStore(store, name, clazz));
    }
    
    public <T extends ResourceInfo> List<T> getResourcesByStore(
            StoreInfo store, Class<T> clazz) {
        return filterResources(user(), delegate.getResourcesByStore(store, clazz));
    }

    public <T extends StoreInfo> T getStore(String id, Class<T> clazz) {
        return checkAccess(user(), delegate.getStore(id, clazz));
    }

    public <T extends StoreInfo> T getStoreByName(String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getStoreByName(name, clazz));
    }

    public <T extends StoreInfo> T getStoreByName(String workspaceName,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getStoreByName(workspaceName, name, clazz));
    }
    
    public <T extends StoreInfo> T getStoreByName(WorkspaceInfo workspace,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getStoreByName(workspace, name, clazz));
    }

    public <T extends StoreInfo> List<T> getStores(Class<T> clazz) {
        return filterStores(user(), delegate.getStores(clazz));
    }

    public <T extends StoreInfo> List<T> getStoresByWorkspace(
            String workspaceName, Class<T> clazz) {
        return filterStores(user(), delegate.getStoresByWorkspace(workspaceName , clazz));
    }

    public <T extends StoreInfo> List<T> getStoresByWorkspace(WorkspaceInfo workspace,
            Class<T> clazz) {
        return filterStores(user(), delegate.getStoresByWorkspace(workspace, clazz));
    }

    public WorkspaceInfo getWorkspace(String id) {
        return checkAccess(user(), delegate.getWorkspace(id));
    }

    public WorkspaceInfo getWorkspaceByName(String name) {
        return checkAccess(user(), delegate.getWorkspaceByName(name));
    }

    public List<WorkspaceInfo> getWorkspaces() {
        return filterWorkspaces(user(), delegate.getWorkspaces());
    }

    // -------------------------------------------------------------------
    // Security support method
    // -------------------------------------------------------------------

    protected static Authentication user() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @SuppressWarnings("unchecked")
    protected <T extends CatalogInfo> T checkAccess(Authentication user, T info) {
        if (info instanceof WorkspaceInfo) {
            return (T) checkAccess(user, (WorkspaceInfo) info);
        }
        if (info instanceof NamespaceInfo) {
            return (T) checkAccess(user, (NamespaceInfo) info);
        }
        if (info instanceof StoreInfo) {
            return (T) checkAccess(user, (StoreInfo) info);
        }
        if (info instanceof ResourceInfo) {
            return (T) checkAccess(user, (ResourceInfo) info);
        }
        if (info instanceof LayerInfo) {
            return (T) checkAccess(user, (LayerInfo) info);
        }
        if (info instanceof LayerGroupInfo) {
            return (T) checkAccess(user, (LayerGroupInfo) info);
        }

        return info;
    }

    /**
     * Given a {@link FeatureTypeInfo} and a user, returns it back if the user
     * can access it in write mode, makes it read only if the user can access it
     * in read only mode, returns null otherwise
     * @return
     */
    protected <T extends ResourceInfo> T checkAccess(Authentication user,
            T info) {
        // handle null case
        if (info == null)
            return null;
        
        // first off, handle the case where the user cannot even read the data
        WrapperPolicy policy = buildWrapperPolicy(user, info, info.getName());
        
        // handle the modes that do not require wrapping
        if(policy.level == AccessLevel.HIDDEN)
            return null;
        else if(policy.level == AccessLevel.READ_WRITE && policy.getLimits() == null)
            return info;
        
        // otherwise we are in a mixed case where the user can read but not write, or
        // cannot read but is allowed by the operation mode to access the metadata
        if(info instanceof FeatureTypeInfo) { 
            return (T) new SecuredFeatureTypeInfo((FeatureTypeInfo) info, policy);
        } else if(info instanceof CoverageInfo) {
            return (T) new SecuredCoverageInfo((CoverageInfo) info, policy);
        } else if(info instanceof WMSLayerInfo) {
            return (T) new SecuredWMSLayerInfo((WMSLayerInfo) info, policy);
        } else {
            throw new RuntimeException("Unknown resource type " + info.getClass());
        }
   }

    /**
     * Given a {@link StyleInfo} and a user, returns it back if the user can access it.
     * 
     * @return <code>null</code> if the user can't acess the style, otherwise the original style.
     */
    protected StyleInfo checkAccess(Authentication user, StyleInfo style) {
        if (style == null)
            return null;
        
        WrapperPolicy policy = buildWrapperPolicy(user, style, style.getName());
        
        // if we don't need to hide it, then we can return it as is since it
        // can only provide metadata.
        if(policy.level == AccessLevel.HIDDEN)
            return null;
        else 
            return style;
    }

    /**
     * Given a store and a user, returns it back if the user can access its
     * workspace in read mode, null otherwise
     * @return
     */
    protected <T extends StoreInfo> T checkAccess(Authentication user, T store) {
        if (store == null)
            return null;
        
        WrapperPolicy policy = buildWrapperPolicy(user, store.getWorkspace(), store.getName());
        
        // handle the modes that do not require wrapping
        if(policy.level == AccessLevel.HIDDEN)
            return null;
        else if(policy.level == AccessLevel.READ_WRITE || 
                (policy.level == AccessLevel.READ_ONLY && store instanceof CoverageStoreInfo))
            return store;

        // otherwise we are in a mixed case where the user can read but not
        // write, or
        // cannot read but is allowed by the operation mode to access the
        // metadata
        if (store instanceof DataStoreInfo) {
            return (T) new SecuredDataStoreInfo((DataStoreInfo) store, policy);
        } else if(store instanceof CoverageStoreInfo) {
            return (T) new SecuredCoverageStoreInfo((CoverageStoreInfo) store, policy);
        } else if (store instanceof WMSStoreInfo) {
            // TODO: implement WMSStoreInfo wrappring if necessary
            return store;
        } else {
            throw new RuntimeException("Unknown store type " + store.getClass());
        }
    }

    /**
     * Given a layer and a user, returns it back if the user can access it, null
     * otherwise
     * @return
     */
    protected LayerInfo checkAccess(Authentication user, LayerInfo layer) {
        if (layer == null)
            return null;
        
        // first off, handle the case where the user cannot even read the data
        WrapperPolicy policy = buildWrapperPolicy(user, layer, layer.getName());
        
        // handle the modes that do not require wrapping
        if(policy.level == AccessLevel.HIDDEN)
            return null;
        else if(policy.level == AccessLevel.READ_WRITE && policy.getLimits() == null)
            return layer;

        // otherwise we are in a mixed case where the user can read but not write, or
        // cannot read but is allowed by the operation mode to access the metadata
        return new SecuredLayerInfo(layer, policy);
    }

    /**
     * Given a layer group and a user, returns it back if the user can access
     * it, null otherwise
     * @return
     */
    protected LayerGroupInfo checkAccess(Authentication user, LayerGroupInfo group) {
        if (group == null)
            return null;

        //first check the layer group itself
        WrapperPolicy policy = buildWrapperPolicy(user, group, group.getName());
        if(policy.level == AccessLevel.HIDDEN) {
            return null;
        }

        boolean needsWrapping = false;
        
        LayerInfo rootLayer = group.getRootLayer();
        if (LayerGroupInfo.Mode.EO.equals(group.getMode())) {
            LayerInfo checked = checkAccess(user, rootLayer);
            if (checked != null) {
                if (checked != rootLayer) {
                    needsWrapping = true;
                    rootLayer = checked;
                }
            } else {
                return null;
            }
        }
        
        // scan thru the layers
        final List<PublishedInfo> layers = group.getLayers();
        ArrayList<PublishedInfo> wrapped = new ArrayList<PublishedInfo>(layers.size());        
        for (PublishedInfo layer : layers) {
            PublishedInfo checked = checkAccess(user, layer);
            if (checked != null) {
                if (checked != layer) {
                    needsWrapping = true;
                }
                wrapped.add(checked);
            } else {
                needsWrapping = true; 
            }
        }
        
        if(needsWrapping)
            return new SecuredLayerGroupInfo(group, rootLayer, wrapped);
        else
            return group;            
    }

    /**
     * Given a namespace and user, returns it back if the user can access it,
     * null otherwise
     * @return
     */
    protected <T extends NamespaceInfo> T checkAccess(Authentication user, T ns) {
        if(ns == null)
            return null;
        
        // route the security check thru the associated workspace info
        WorkspaceInfo ws = delegate.getWorkspaceByName(ns.getPrefix());
        if(ws == null) {
            // temporary workaround, build a fake workspace, as we're probably
            // in between a change of workspace/namespace name
            ws = delegate.getFactory().createWorkspace();
            ws.setName(ns.getPrefix());
        }
        WorkspaceInfo info = checkAccess(user, ws);
        if (info == null)
            return null;
        else
            return ns;
    }

    /**
     * Given a workspace and user, returns it back if the user can access it,
     * null otherwise
     * @return
     */
    protected <T extends WorkspaceInfo> T checkAccess(Authentication user, T ws) {
        if (ws == null)
            return null;
        
        WrapperPolicy policy = buildWrapperPolicy(user, ws, ws.getName());
        
        // if we don't need to hide it, then we can return it as is since it
        // can only provide metadata.
        if(policy.level == AccessLevel.HIDDEN)
            return null;
        else 
            return ws;
    }

    protected WrapperPolicy buildWrapperPolicy(Authentication user, @Nonnull CatalogInfo info) {
        Assert.notNull(info);

        if (info instanceof NamespaceInfo) {
            // route the security check thru the associated workspace info
            WorkspaceInfo ws = delegate.getWorkspaceByName(((NamespaceInfo) info).getPrefix());
            if (ws == null) {
                // temporary workaround, build a fake workspace, as we're
                // probably
                // in between a change of workspace/namespace name
                ws = delegate.getFactory().createWorkspace();
                ws.setName(((NamespaceInfo) info).getPrefix());
            }
            return buildWrapperPolicy(user, ws, ws.getName());

        }

        if (info instanceof WorkspaceInfo) {
            return buildWrapperPolicy(user, info, ((WorkspaceInfo) info).getName());
        }

        if (info instanceof StoreInfo) {
            return buildWrapperPolicy(user, ((StoreInfo) info).getWorkspace(),
                    ((StoreInfo) info).getName());
        }

        if (info instanceof ResourceInfo) {
            return buildWrapperPolicy(user, info, ((ResourceInfo) info).getName());
        }

        if (info instanceof LayerInfo) {
            return buildWrapperPolicy(user, info, ((LayerInfo) info).getName());
        }

        if (info instanceof LayerGroupInfo) {
            // return the most restrictive policy that's not HIDDEN, or the
            // first HIDDEN one
            WrapperPolicy mostRestrictive = WrapperPolicy.readWrite(null);

            for (PublishedInfo layer : ((LayerGroupInfo) info).getLayers()) {
                WrapperPolicy policy = buildWrapperPolicy(user, layer, layer.getName());
                if (AccessLevel.HIDDEN.equals(policy.getAccessLevel())) {
                    return policy;
                }
                int comparison = policy.compareTo(mostRestrictive);
                boolean thisOneIsMoreRestrictive = comparison < 0;
                if (thisOneIsMoreRestrictive) {
                    mostRestrictive = policy;
                }
            }

            return mostRestrictive;
        }

        throw new IllegalArgumentException("Can't build wrapper policy for objects of type "
                + info.getClass().getName());

    }

    /**
     * Factors out the policy that decides what access level the current user
     * has to a specific resource considering the read/write access, the security
     * mode, and the filtering status
     * @param user
     * @param canRead
     * @param canWrite
     * @param resourceName
     * @return
     */
    public WrapperPolicy buildWrapperPolicy(Authentication user,
            CatalogInfo info, String resourceName) {
        boolean canRead = true;
        boolean canWrite = true;

        AccessLimits limits;

        if(info instanceof WorkspaceInfo) {
            // unsure here... shall we disallow writing? Only catalog and config
            // related code should be playing with stores directly, so it's more of a 
            // matter if you can admin a workspace or not
            limits = accessManager.getAccessLimits(user, (WorkspaceInfo) info);
            WorkspaceAccessLimits wl = (WorkspaceAccessLimits) limits;
            if(wl != null) {
                if (wl.isAdminable()) {
                    canRead = canWrite = true;
                }
                else {
                    canRead = wl.isReadable();
                    canWrite = wl.isWritable();
                }
            }
            if (AdminRequest.get() != null) {
                //admin request
                if (wl == null || !wl.isAdminable()) {
                    canRead = canWrite = false;
                }
            }
        } else if(info instanceof LayerInfo || info instanceof ResourceInfo) {
            DataAccessLimits dl;
            WorkspaceAccessLimits wl;
            
            if(info instanceof LayerInfo) {
                dl = accessManager.getAccessLimits(user, (LayerInfo) info);
                wl = accessManager.getAccessLimits(user, 
                    ((LayerInfo)info).getResource().getStore().getWorkspace());
            } else {
                dl = accessManager.getAccessLimits(user, (ResourceInfo) info);
                wl = accessManager.getAccessLimits(user, 
                    ((ResourceInfo)info).getStore().getWorkspace());
            }
            if(dl != null) {
                canRead = dl.getReadFilter() != Filter.EXCLUDE;
                if(dl instanceof VectorAccessLimits) {
                    canWrite = ((VectorAccessLimits) dl).getWriteFilter() != Filter.EXCLUDE;
                } else {
                    canWrite = false;
                }
            }
            limits = dl;

            if (AdminRequest.get() != null) {
                if (wl != null && !wl.isAdminable()) {
                    canRead = false;
                }
            }
        } else if (info instanceof StyleInfo || info instanceof LayerGroupInfo) {
            WorkspaceInfo ws = null;
            if (info instanceof StyleInfo) {
                limits = accessManager.getAccessLimits(user, (StyleInfo) info);
                ws = ((StyleInfo)info).getWorkspace();
            }
            else {
                limits = accessManager.getAccessLimits(user, (LayerGroupInfo) info);
                ws = ((LayerGroupInfo)info).getWorkspace();
            }
            
            if (limits != null) {
                canRead = false;
            }

            if (ws != null && AdminRequest.get() != null) {
                WorkspaceAccessLimits wl = accessManager.getAccessLimits(user,  ws);
                if (wl != null) {
                    if (!wl.isAdminable()) {
                        canRead = false;
                    }
                }
            }
        }else {
            throw new IllegalArgumentException("Can't build the wrapper policy for objects " +
            		"other than workspace, layer or resource: " + info);
        }
        
        final CatalogMode mode = limits != null ? limits.getMode() : CatalogMode.HIDE;
        if (!canRead) {
            // if in hide mode, we just hide the resource
            if (mode == CatalogMode.HIDE) {
                return WrapperPolicy.hide(limits);
            } else if (mode == CatalogMode.MIXED) {
                // if request is a get capabilities and mixed, we hide again
                Request request = Dispatcher.REQUEST.get();
                if(request != null && "GetCapabilities".equalsIgnoreCase(request.getRequest()))
                    return WrapperPolicy.hide(limits);
                // otherwise challenge the user for credentials
                else
                    throw unauthorizedAccess(resourceName);
            } else {
                // for challenge mode we agree to show freely only the metadata, every
                // other access will trigger a security exception
                return WrapperPolicy.metadata(limits);
            }
        } else if (!canWrite) {
            if (mode == CatalogMode.HIDE) {
                return WrapperPolicy.readOnlyHide(limits);
            } else {
                return WrapperPolicy.readOnlyChallenge(limits);
            }
        }

        return WrapperPolicy.readWrite(limits);
    }

    public static RuntimeException unauthorizedAccess(String resourceName) {
        // not hide, and not filtering out a list, this
        // is an unauthorized direct resource access, complain
        Authentication user = user();
        if (user == null || user.getAuthorities().size() == 0)
            return new InsufficientAuthenticationException("Cannot access "
                    + resourceName + " as anonymous");
        else
            return new AccessDeniedException("Cannot access "
                    + resourceName + " with the current privileges");
    }
    
    public static RuntimeException unauthorizedAccess() {
        // not hide, and not filtering out a list, this
        // is an unauthorized direct resource access, complain
        Authentication user = user();
        if (user == null || user.getAuthorities().size() == 0)
            return new InsufficientAuthenticationException("Operation unallowed with the current privileges");
        else
            return new AccessDeniedException("Operation unallowed with the current privileges");
    }

    /**
     * Given a list of resources, returns a copy of it containing only the
     * resources the user can access
     * 
     * @param user
     * @param resources
     * 
     * @return
     */
    protected <T extends ResourceInfo> List<T> filterResources(Authentication user,
            List<T> resources) {
        List<T> result = new ArrayList<T>();
        for (T original : resources) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of stores, returns a copy of it containing only the
     * resources the user can access
     * 
     * @param user
     * @param resources
     * 
     * @return
     */
    protected <T extends StoreInfo> List<T> filterStores(Authentication user, List<T> resources) {
        List<T> result = new ArrayList<T>();
        for (T original : resources) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of layer groups, returns a copy of it containing only the
     * groups the user can access
     * 
     * @param user
     * @param groups
     * 
     * @return
     */
    protected List<LayerGroupInfo> filterGroups(Authentication user, List<LayerGroupInfo> groups) {
        List<LayerGroupInfo> result = new ArrayList<LayerGroupInfo>();
        for (LayerGroupInfo original : groups) {
            LayerGroupInfo secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of layers, returns a copy of it containing only the layers
     * the user can access
     * 
     * @param user
     * @param layers
     * 
     * @return
     */
    protected List<LayerInfo> filterLayers(Authentication user, List<LayerInfo> layers) {
        List<LayerInfo> result = new ArrayList<LayerInfo>();
        for (LayerInfo original : layers) {
            LayerInfo secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of styles, returns a copy of it containing only the styles the user can access.
     * 
     */
    protected List<StyleInfo> filterStyles(Authentication user, List<StyleInfo> styles) {
        List<StyleInfo> result = new ArrayList<StyleInfo>();
        for (StyleInfo original : styles) {
            StyleInfo secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of namespaces, returns a copy of it containing only the
     * namespaces the user can access
     * 
     * @param user
     * @param namespaces
     * 
     * @return
     */
    protected <T extends NamespaceInfo> List<T> filterNamespaces(Authentication user,
            List<T> namespaces) {
        List<T> result = new ArrayList<T>();
        for (T original : namespaces) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of workspaces, returns a copy of it containing only the
     * workspaces the user can access
     * 
     * @param user
     * @param namespaces
     * 
     * @return
     */
    protected <T extends WorkspaceInfo> List<T> filterWorkspaces(Authentication user,
            List<T> workspaces) {
        List<T> result = new ArrayList<T>();
        for (T original : workspaces) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }
    
    // -------------------------------------------------------------------
    // Unwrappers, used to make sure the lower level does not get hit by
    // read only wrappers
    // -------------------------------------------------------------------
    static LayerGroupInfo unwrap(LayerGroupInfo layerGroup) {
        if(layerGroup instanceof SecuredLayerGroupInfo)
            return ((SecuredLayerGroupInfo) layerGroup).unwrap(LayerGroupInfo.class);
        return layerGroup;
    }
    
    static LayerInfo unwrap(LayerInfo layer) {
        if(layer instanceof SecuredLayerInfo)
            return ((SecuredLayerInfo) layer).unwrap(LayerInfo.class);
        return layer;
    }
    
    static ResourceInfo unwrap(ResourceInfo info) {
        if(info instanceof SecuredFeatureTypeInfo)
            return ((SecuredFeatureTypeInfo) info).unwrap(ResourceInfo.class);
        if (info instanceof SecuredCoverageInfo)
            return ((SecuredCoverageInfo) info).unwrap(ResourceInfo.class);

        return info;
    }
    
    static StoreInfo unwrap(StoreInfo info) {
        if(info instanceof SecuredDataStoreInfo)
            return ((SecuredDataStoreInfo) info).unwrap(StoreInfo.class);
        return info;
    }

    public static Object unwrap( Object obj ) {
        if ( obj instanceof LayerGroupInfo ) {
            return unwrap((LayerGroupInfo)obj);
        }
        if ( obj instanceof LayerInfo ) {
            return unwrap((LayerInfo)obj);
        }
        if ( obj instanceof ResourceInfo ) {
            return unwrap((ResourceInfo)obj);
        }
        if ( obj instanceof StoreInfo ) {
            return unwrap((StoreInfo)obj);
        }
        if ( obj instanceof SecureCatalogImpl ) {
            return ((SecureCatalogImpl)obj).delegate;
        }
        
        return obj;
    }
    // -------------------------------------------------------------------
    // PURE DELEGATING METHODS
    // (MapInfo being here since its role in the grand scheme of things
    // is still undefined)
    // -------------------------------------------------------------------

    public MapInfo getMap(String id) {
        return delegate.getMap(id);
    }

    public MapInfo getMapByName(String name) {
        return delegate.getMapByName(name);
    }

    public List<MapInfo> getMaps() {
        return delegate.getMaps();
    }

    public void add(LayerGroupInfo layerGroup) {
        delegate.add(unwrap(layerGroup));
    }

    public List<RuntimeException> validate(LayerGroupInfo layerGroup, boolean isNew) {
        return delegate.validate(unwrap(layerGroup), isNew);
    }

    public LayerGroupInfo detach(LayerGroupInfo layerGroup) {
        return delegate.detach(layerGroup);
    }
    
    public void add(LayerInfo layer) {
        delegate.add(unwrap(layer));
    }

    public List<RuntimeException> validate(LayerInfo layer, boolean isNew) {
        return delegate.validate(unwrap(layer), isNew);
    }

    public LayerInfo detach(LayerInfo layer) {
        return delegate.detach(layer);
    }
    
    public void add(MapInfo map) {
        delegate.add(map);
    }
    
    public MapInfo detach(MapInfo map) {
        return delegate.detach(map);
    }

    public void add(NamespaceInfo namespace) {
        delegate.add(namespace);
    }

    public List<RuntimeException> validate(NamespaceInfo namespace, boolean isNew) {
        return delegate.validate(namespace, isNew);
    }

    public NamespaceInfo detach(NamespaceInfo namespace) {
        return delegate.detach(namespace);
    }

    public void add(ResourceInfo resource) {
        delegate.add(unwrap(resource));
    }

    public List<RuntimeException> validate(ResourceInfo resource, boolean isNew) {
        return delegate.validate(unwrap(resource), isNew);
    }

    public <T extends ResourceInfo> T detach(T resource) {
        return delegate.detach(resource);
    }
    
    public void add(StoreInfo store) {
        delegate.add(unwrap(store));
    }

    public List<RuntimeException> validate(StoreInfo store, boolean isNew) {
        return delegate.validate(unwrap(store), isNew);
    }

    
    public <T extends StoreInfo> T detach(T store) {
        return delegate.detach(store);
    }

    public void add(StyleInfo style) {
        delegate.add(style);
    }

    public List<RuntimeException> validate(StyleInfo style, boolean isNew) {
        return delegate.validate(style, isNew);
    }

    public StyleInfo detach(StyleInfo style) {
        return delegate.detach(style);
    }

    public void add(WorkspaceInfo workspace) {
        delegate.add(workspace);
    }

    public List<RuntimeException> validate(WorkspaceInfo workspace, boolean isNew) {
        return delegate.validate(workspace, isNew);
    }

    public WorkspaceInfo detach(WorkspaceInfo workspace) {
        return delegate.detach(workspace);
    }
    
    public void addListener(CatalogListener listener) {
        delegate.addListener(listener);
    }

    public void dispose() {
        delegate.dispose();
    }

    public CatalogFacade getFacade() {
        return delegate.getFacade();
    }
    
    public CatalogFactory getFactory() {
        return delegate.getFactory();
    }

    public Collection<CatalogListener> getListeners() {
        return delegate.getListeners();
    }

    public void fireAdded(CatalogInfo object) {
        delegate.fireAdded(object);
    }
    
    public void fireModified(CatalogInfo object, List<String> propertyNames, List oldValues,
            List newValues) {
        delegate.fireModified(object, propertyNames, oldValues, newValues);
    }
    
    public void firePostModified(CatalogInfo object) {
        delegate.firePostModified(object);
    }
    
    public void fireRemoved(CatalogInfo object) {
        delegate.fireRemoved(object);
    }
    
    // TODO: why is resource pool being exposed???
    public ResourcePool getResourcePool() {
        return delegate.getResourcePool();
    }

    public StyleInfo getStyle(String id) {
        return delegate.getStyle(id);
    }

    public StyleInfo getStyleByName(String name) {
        return checkAccess(user(), delegate.getStyleByName(name));
    }

    public StyleInfo getStyleByName(String workspaceName, String name) {
        return checkAccess(user(), delegate.getStyleByName(workspaceName, name)); 
    }

    public StyleInfo getStyleByName(WorkspaceInfo workspace, String name) {
        return checkAccess(user(), delegate.getStyleByName(workspace, name));
    }

    public List<StyleInfo> getStyles() {
        return filterStyles(user(),delegate.getStyles());
    }

    public List<StyleInfo> getStylesByWorkspace(String workspaceName) {
        return filterStyles(user(),delegate.getStylesByWorkspace(workspaceName));
    }

    public List<StyleInfo> getStylesByWorkspace(WorkspaceInfo workspace) {
        return filterStyles(user(),delegate.getStylesByWorkspace(workspace));
    }

    public void remove(LayerGroupInfo layerGroup) {
        delegate.remove(unwrap(layerGroup));
    }

    public void remove(LayerInfo layer) {
        delegate.remove(unwrap(layer));
    }

    public void remove(MapInfo map) {
        delegate.remove(map);
    }

    public void remove(NamespaceInfo namespace) {
        delegate.remove(namespace);
    }

    public void remove(ResourceInfo resource) {
        delegate.remove(unwrap(resource));
    }

    public void remove(StoreInfo store) {
        delegate.remove(unwrap(store));
    }

    public void remove(StyleInfo style) {
        delegate.remove(style);
    }

    public void remove(WorkspaceInfo workspace) {
        delegate.remove(workspace);
    }

    public void removeListener(CatalogListener listener) {
        delegate.removeListener(listener);
    }

    public void save(LayerGroupInfo layerGroup) {
        delegate.save(unwrap(layerGroup));
    }

    public void save(LayerInfo layer) {
        delegate.save(unwrap(layer));
    }

    public void save(MapInfo map) {
        delegate.save(map);
    }

    public void save(NamespaceInfo namespace) {
        delegate.save(namespace);
    }

    public void save(ResourceInfo resource) {
        delegate.save(unwrap(resource));
    }

    public void save(StoreInfo store) {
        delegate.save(unwrap(store));
    }

    public void save(StyleInfo style) {
        delegate.save(style);
    }

    public void save(WorkspaceInfo workspace) {
        delegate.save(workspace);
    }

    public void setDefaultNamespace(NamespaceInfo defaultNamespace) {
        delegate.setDefaultNamespace(defaultNamespace);
    }

    public void setDefaultWorkspace(WorkspaceInfo workspace) {
        delegate.setDefaultWorkspace(workspace);
    }

    public void setResourcePool(ResourcePool resourcePool) {
        delegate.setResourcePool(resourcePool);
    }
    
    public GeoServerResourceLoader getResourceLoader() {
        return delegate.getResourceLoader();
    }
    
    public void setResourceLoader(GeoServerResourceLoader resourceLoader) {
        delegate.setResourceLoader(resourceLoader);
    }
    
    public void accept(CatalogVisitor visitor) {
        delegate.accept(visitor);
    }

    public DataStoreInfo getDefaultDataStore(WorkspaceInfo workspace) {
        return checkAccess(user(), delegate.getDefaultDataStore(workspace));
    }

    public void setDefaultDataStore(WorkspaceInfo workspace, DataStoreInfo defaultStore) {
        delegate.setDefaultDataStore(workspace, defaultStore);
    }

    @Override
    public <T extends CatalogInfo> int count(Class<T> of, Filter filter) {
        Filter securityFilter = securityFilter(of, filter);
        final int count = delegate.count(of, securityFilter);
        return count;
    }

    @Override
    public <T extends CatalogInfo> T get(Class<T> type, Filter filter)
            throws IllegalArgumentException {

        Filter securityFilter = securityFilter(type, filter);
        T result = delegate.get(type, securityFilter);
        return result;
    }

    @Override
    public <T extends CatalogInfo> CloseableIterator<T> list(Class<T> of, Filter filter) {
        return list(of, filter, null, null, null);
    }

    @Override
    public <T extends CatalogInfo> CloseableIterator<T> list(Class<T> of, Filter filter,
            Integer offset, Integer count, SortBy sortBy) {

        Filter securityFilter = securityFilter(of, filter);

        CloseableIterator<T> filtered;
        filtered = delegate.list(of, securityFilter, offset, count, sortBy);

        // create secured decorators on-demand
        final Function<T, T> securityWrapper = securityWrapper(of);
        final CloseableIterator<T> filteredWrapped;
        filteredWrapped = CloseableIteratorAdapter.transform(filtered, securityWrapper);

        return filteredWrapped;
    }

    /**
     * @return a Function that applies a security wrapper over the catalog object given to it as
     *         input
     * @see #checkAccess(Authentication, CatalogInfo)
     */
    private <T extends CatalogInfo> Function<T, T> securityWrapper(final Class<T> forClass) {

        final Authentication user = user();
        return new Function<T, T>() {

            @Override
            public T apply(T input) {
                T checked = checkAccess(user, input);
                return checked;
            }
        };
    }

    /**
     * Returns a predicate that checks whether the current user has access to a given object of type
     * {@code infoType}.
     * <p>
     * IMPLEMENTATION NOTE: the predicate returned evaluates in-process and hence can't be encoded
     * to the catalog's native query language, if any. It calls
     * {@link #buildWrapperPolicy(Authentication, CatalogInfo)} to check if the returned access
     * level is not "hidden" on a case by case basis. Perhaps, the check for whether a given
     * resource is accessible to the current user can be encoded as a "well known" predicate that
     * uses one or a combination of the property equals/isnull/contains/exists verbs in the
     * {@link Predicates} utility. I (GR), at the time of writing, don't know how to do that, so any
     * help would be much appreciated. Nonetheless, this predicate is meant to be "and'ed" with any
     * other predicate this catalog wrapper is called with, giving the Catalog backend a chance to
     * at least encode the "well known" part of the resulting filter, and separate out the
     * in-process evaluation of access credentials from the construction of the security wrapper for
     * each object.
     * 
     * @return a catalog Predicate that evaluates if an object of the required type is accessible to
     *         the given user
     */
    private <T extends CatalogInfo> Filter securityFilter(final Class<T> infoType,
            final Filter filter) {

        final Authentication user = user();
        if (isAdmin(user)) {
            // no need to check for credentials if user is _the_ administrator
            return filter;
        }

        if (StyleInfo.class.isAssignableFrom(infoType) || MapInfo.class.isAssignableFrom(infoType)) {
            // these kind of objects are not secured
            return filter;
        }

        org.opengis.filter.expression.Function visible = new InternalVolatileFunction() {
            /**
             * Returns {@code false} if the catalog info shall be hidden, {@code true} otherwise.
             */
            @Override
            public Boolean evaluate(Object object) {
                WrapperPolicy policy = buildWrapperPolicy(user, (CatalogInfo)object);
                AccessLevel accessLevel = policy.getAccessLevel();
                boolean visible = !AccessLevel.HIDDEN.equals(accessLevel);
                return Boolean.valueOf(visible);
            }
        };

        FilterFactory factory = Predicates.factory;

        // create a filter combined with the security credentials check
        Filter securityFilter = factory.equals(factory.literal(Boolean.TRUE), visible);
        return Predicates.and(filter, securityFilter);
    }

    /**
     * Checks if the current user is authenticated and is the administrator
     */
    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            return false;

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMINISTRATOR".equals(authority.getAuthority()))
                return true;
        }
        return false;
    }

    public void removeListeners(Class listenerClass) {
        delegate.removeListeners(listenerClass);
    }
}
